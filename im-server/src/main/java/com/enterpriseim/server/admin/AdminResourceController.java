package com.enterpriseim.server.admin;

import lombok.val;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.call.CallService;
import com.enterpriseim.server.config.ImProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/admin")
public class AdminResourceController {
    private final JdbcTemplate jdbcTemplate;
    private final AdminAuthService authService;
    private final CallService callService;
    private final ImProperties properties;

    public AdminResourceController(JdbcTemplate jdbcTemplate, AdminAuthService authService, CallService callService, ImProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.callService = callService;
        this.properties = properties;
    }

    @GetMapping("/groups")
    public ApiResponse<List<GroupRow>> groups(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String enterpriseId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        authService.requireAdmin(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        val sql = new StringBuilder("SELECT g.id, g.enterprise_id, e.name AS enterprise_name, g.owner_id, g.name, g.status, g.created_at,\n" +
                "       (SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = g.id) AS member_count\n" +
                "FROM chat_groups g\n" +
                "LEFT JOIN enterprises e ON e.id = g.enterprise_id\n" +
                "WHERE 1 = 1\n");
        val params = new ArrayList<Object>();
        if (hasText(enterpriseId)) {
            sql.append(" AND g.enterprise_id = ?");
            params.add(enterpriseId.trim());
        }
        if (hasText(status)) {
            sql.append(" AND g.status = ?");
            params.add(status.trim());
        }
        sql.append(" ORDER BY g.created_at DESC, g.id ASC LIMIT ?");
        params.add(boundedLimit);
        return ApiResponse.ok(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new GroupRow(
                rs.getString("id"),
                rs.getString("enterprise_id"),
                rs.getString("enterprise_name"),
                rs.getString("owner_id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getInt("member_count"),
                toInstant(rs.getTimestamp("created_at"))
        ), params.toArray()));
    }

    @GetMapping("/files")
    public ApiResponse<List<FileRow>> files(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String uploaderId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit
    ) {
        authService.requireAdmin(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        val sql = new StringBuilder("SELECT id, uploader_id, original_name, content_type, size_bytes, status, created_at\n" +
                "FROM files\n" +
                "WHERE 1 = 1\n");
        val params = new ArrayList<Object>();
        if (hasText(uploaderId)) {
            sql.append(" AND uploader_id = ?");
            params.add(uploaderId.trim());
        }
        if (hasText(status)) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        sql.append(" ORDER BY created_at DESC, id ASC LIMIT ?");
        params.add(boundedLimit);
        return ApiResponse.ok(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new FileRow(
                rs.getString("id"),
                rs.getString("uploader_id"),
                rs.getString("original_name"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                "/api/files/" + rs.getString("id") + "/download",
                "/api/files/" + rs.getString("id") + "/preview"
        ), params.toArray()));
    }

    @GetMapping("/files/{fileId}/transfers")
    public ApiResponse<List<FileTransferRow>> fileTransfers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String fileId
    ) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(jdbcTemplate.query(
                "SELECT id, file_id, user_id, direction, progress, status, created_at, updated_at\n" +
                        "FROM file_transfer_logs WHERE file_id = ? ORDER BY updated_at DESC, id ASC LIMIT 100",
                (rs, rowNum) -> new FileTransferRow(
                        rs.getString("id"),
                        rs.getString("file_id"),
                        rs.getString("user_id"),
                        rs.getString("direction"),
                        rs.getInt("progress"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("updated_at"))
                ),
                fileId));
    }

    @PatchMapping("/files/{fileId}/status")
    public ApiResponse<FileRow> updateFileStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String fileId,
            @RequestBody FileStatusRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN", "SECURITY_AUDITOR");
        val status = request.status();
        if (!Arrays.asList("available", "disabled", "deleted").contains(status)) {
            throw new ResponseStatusException(BAD_REQUEST, "文件状态必须为 available、disabled 或 deleted");
        }
        ensureFile(fileId);
        jdbcTemplate.update("UPDATE files SET status = ? WHERE id = ?", status, fileId);
        audit(admin.userId(), "FILE_STATUS_UPDATE", "file", fileId, "status=" + status);
        return ApiResponse.ok(fileById(fileId));
    }

    @DeleteMapping("/files/{fileId}")
    public ApiResponse<FileRow> deleteFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String fileId,
            @RequestBody ConfirmRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        if (!"CONFIRM".equals(request.confirmText())) {
            throw new ResponseStatusException(BAD_REQUEST, "确认文本必须为 CONFIRM");
        }
        ensureFile(fileId);
        jdbcTemplate.update("UPDATE files SET status = 'deleted' WHERE id = ?", fileId);
        audit(admin.userId(), "FILE_DELETE", "file", fileId, "soft deleted file resource");
        return ApiResponse.ok(fileById(fileId));
    }

    @PostMapping("/files/lifecycle-cleanup")
    public ApiResponse<FileCleanupResult> cleanupExpiredFiles(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val retentionDays = Integer.parseInt(resourcePolicy("file_retention_days", "365"));
        val cutoff = Timestamp.from(Instant.now().minusSeconds(Math.max(0, retentionDays) * 86400L));
        val files = jdbcTemplate.query("SELECT id, object_key FROM files WHERE status <> 'deleted' AND created_at <= ?",
                (rs, rowNum) -> new StoredFileRef(rs.getString("id"), rs.getString("object_key")), cutoff);
        int physicalDeleted = 0;
        for (StoredFileRef file : files) {
            val path = storagePath(file.objectKey);
            try {
                if (Files.deleteIfExists(path)) physicalDeleted++;
            } catch (Exception ignored) {
                // DB state is still authoritative; storage cleanup is best effort.
            }
            jdbcTemplate.update("UPDATE files SET status = 'deleted' WHERE id = ?", file.id);
        }
        audit(admin.userId(), "FILE_LIFECYCLE_CLEANUP", "file", "expired", "files=" + files.size());
        return ApiResponse.ok(new FileCleanupResult(files.size(), physicalDeleted, retentionDays));
    }

    @GetMapping("/messages")
    public ApiResponse<List<MessageAuditRow>> messages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String senderId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        authService.requireAdmin(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        val sql = new StringBuilder("SELECT id, conversation_id, sender_id, type, content, status, created_at\n" +
                "FROM messages\n" +
                "WHERE 1 = 1\n");
        val params = new ArrayList<Object>();
        if (hasText(conversationId)) {
            sql.append(" AND conversation_id = ?");
            params.add(conversationId.trim());
        }
        if (hasText(senderId)) {
            sql.append(" AND sender_id = ?");
            params.add(senderId.trim());
        }
        sql.append(" ORDER BY created_at DESC, id ASC LIMIT ?");
        params.add(boundedLimit);
        return ApiResponse.ok(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new MessageAuditRow(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("sender_id"),
                rs.getString("type"),
                rs.getString("content"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at"))
        ), params.toArray()));
    }

    @GetMapping("/calls")
    public ApiResponse<List<CallAuditRow>> calls(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mediaType,
            @RequestParam(defaultValue = "50") int limit
    ) {
        authService.requireAdmin(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        val sql = new StringBuilder("SELECT id, conversation_id, caller_id, callee_id, group_id, media_type, status,\n" +
                "       started_at, answered_at, ended_at, turn_session_id\n" +
                "FROM call_records\n" +
                "WHERE 1 = 1\n");
        val params = new ArrayList<Object>();
        if (hasText(userId)) {
            sql.append(" AND (caller_id = ? OR callee_id = ?)");
            params.add(userId.trim());
            params.add(userId.trim());
        }
        if (hasText(status)) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        if (hasText(mediaType)) {
            sql.append(" AND media_type = ?");
            params.add(mediaType.trim());
        }
        sql.append(" ORDER BY started_at DESC, id ASC LIMIT ?");
        params.add(boundedLimit);
        return ApiResponse.ok(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new CallAuditRow(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("caller_id"),
                rs.getString("callee_id"),
                rs.getString("group_id"),
                rs.getString("media_type"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("answered_at")),
                toInstant(rs.getTimestamp("ended_at")),
                rs.getString("turn_session_id")
        ), params.toArray()));
    }

    @GetMapping("/call-connectivity")
    public ApiResponse<CallService.CallConnectivity> callConnectivity(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(callService.connectivity());
    }

    // ---- Audit query APIs ----

    @GetMapping("/message-edits")
    public ApiResponse<List<Map<String, Object>>> listMessageEdits(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String messageId) {
        authService.requireAdmin(authorization);
        String sql = "SELECT id, message_id, editor_id, old_content, new_content, created_at FROM message_edits";
        List<Object> params = new java.util.ArrayList<>();
        if (messageId != null) { sql += " WHERE message_id = ?"; params.add(messageId); }
        sql += " ORDER BY created_at DESC LIMIT 100";
        return ApiResponse.ok(jdbcTemplate.queryForList(sql, params.toArray()));
    }

    @GetMapping("/message-recalls")
    public ApiResponse<List<Map<String, Object>>> listMessageRecalls(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String conversationId) {
        authService.requireAdmin(authorization);
        String sql = "SELECT mr.id, mr.message_id, mr.operator_id, mr.reason, mr.created_at FROM message_recalls mr";
        List<Object> params = new java.util.ArrayList<>();
        if (conversationId != null) { sql += " JOIN messages m ON m.id = mr.message_id WHERE m.conversation_id = ?"; params.add(conversationId); }
        sql += " ORDER BY mr.created_at DESC LIMIT 100";
        return ApiResponse.ok(jdbcTemplate.queryForList(sql, params.toArray()));
    }

    @GetMapping("/screenshot-events")
    public ApiResponse<List<Map<String, Object>>> listScreenshotEvents(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String conversationId) {
        authService.requireAdmin(authorization);
        String sql = "SELECT id, conversation_id, user_id, created_at FROM screenshot_events";
        List<Object> params = new java.util.ArrayList<>();
        if (conversationId != null) { sql += " WHERE conversation_id = ?"; params.add(conversationId); }
        sql += " ORDER BY created_at DESC LIMIT 100";
        return ApiResponse.ok(jdbcTemplate.queryForList(sql, params.toArray()));
    }

    // ---- Friendships and blacklists ----

    @GetMapping("/friendships")
    public ApiResponse<List<Map<String, Object>>> listFriendships(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String userId) {
        authService.requireAdmin(authorization);
        String sql = "SELECT user_id, friend_id, remark, source, created_at FROM friendships";
        List<Object> params = new java.util.ArrayList<>();
        if (userId != null) { sql += " WHERE user_id = ? OR friend_id = ?"; params.add(userId); params.add(userId); }
        sql += " ORDER BY created_at DESC LIMIT 100";
        return ApiResponse.ok(jdbcTemplate.queryForList(sql, params.toArray()));
    }

    @GetMapping("/blacklists")
    public ApiResponse<List<Map<String, Object>>> listBlacklists(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String userId) {
        authService.requireAdmin(authorization);
        String sql = "SELECT user_id, blocked_user_id, created_at FROM blacklists";
        List<Object> params = new java.util.ArrayList<>();
        if (userId != null) { sql += " WHERE user_id = ?"; params.add(userId); }
        sql += " ORDER BY created_at DESC LIMIT 100";
        return ApiResponse.ok(jdbcTemplate.queryForList(sql, params.toArray()));
    }

    @DeleteMapping("/blacklists/{userId}/{blockedUserId}")
    public ApiResponse<String> removeBlacklist(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String userId,
            @PathVariable String blockedUserId) {
        val admin = authService.requireAdmin(authorization);
        jdbcTemplate.update("DELETE FROM blacklists WHERE user_id = ? AND blocked_user_id = ?", userId, blockedUserId);
        audit(admin.userId(), "BLACKLIST_REMOVE", "blacklist", userId + ":" + blockedUserId, "removed blacklist entry");
        return ApiResponse.ok("ok");
    }

    // ---- Group member management (admin) ----

    @GetMapping("/groups/{groupId}/members")
    public ApiResponse<List<Map<String, Object>>> listGroupMembers(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String groupId) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(jdbcTemplate.queryForList(
                "SELECT gm.user_id, gm.role, gm.alias, gm.muted, gm.joined_at, u.name as user_name FROM group_members gm LEFT JOIN users u ON u.id = gm.user_id WHERE gm.group_id = ? ORDER BY gm.role DESC",
                groupId));
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    public ApiResponse<String> removeGroupMember(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String groupId,
            @PathVariable String userId) {
        val admin = authService.requireAdmin(authorization);
        jdbcTemplate.update("DELETE FROM group_members WHERE group_id = ? AND user_id = ?", groupId, userId);
        audit(admin.userId(), "GROUP_MEMBER_REMOVE", "group_member", groupId + ":" + userId, "removed member from group");
        return ApiResponse.ok("ok");
    }

    @PatchMapping("/groups/{groupId}/members/{userId}/mute")
    public ApiResponse<String> muteGroupMember(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String groupId,
            @PathVariable String userId,
            @RequestBody MuteRequest request) {
        val admin = authService.requireAdmin(authorization);
        jdbcTemplate.update("UPDATE group_members SET muted = ? WHERE group_id = ? AND user_id = ?", request.muted(), groupId, userId);
        audit(admin.userId(), "GROUP_MEMBER_MUTE", "group_member", groupId + ":" + userId, request.muted() ? "muted" : "unmuted");
        return ApiResponse.ok("ok");
    }

    private void audit(String operatorId, String action, String targetType, String targetId, String detail) {
        jdbcTemplate.update("INSERT INTO audit_logs(id, operator_id, action, target_type, target_id, detail) VALUES (?, ?, ?, ?, ?, ?)",
                "audit_" + UUID.randomUUID(), operatorId, action, targetType, targetId, detail);
    }

    private void ensureFile(String fileId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM files WHERE id = ?", Integer.class, fileId);
        if (count == null || count == 0) throw new ResponseStatusException(BAD_REQUEST, "文件未找到");
    }

    private FileRow fileById(String fileId) {
        return jdbcTemplate.query("SELECT id, uploader_id, original_name, content_type, size_bytes, status, created_at FROM files WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "文件未找到");
            return new FileRow(
                    rs.getString("id"),
                    rs.getString("uploader_id"),
                    rs.getString("original_name"),
                    rs.getString("content_type"),
                    rs.getLong("size_bytes"),
                    rs.getString("status"),
                    toInstant(rs.getTimestamp("created_at")),
                    "/api/files/" + rs.getString("id") + "/download",
                    "/api/files/" + rs.getString("id") + "/preview"
            );
        }, fileId);
    }

    private String resourcePolicy(String key, String fallback) {
        return jdbcTemplate.query("SELECT policy_value FROM resource_policies WHERE policy_key = ?", rs -> rs.next() ? rs.getString("policy_value") : fallback, key);
    }

    private Path storagePath(String objectKey) {
        val root = Paths.get(properties.getStorage().getLocalRoot(), properties.getStorage().getBucket()).toAbsolutePath().normalize();
        val target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) throw new ResponseStatusException(BAD_REQUEST, "无效的对象存储路径");
        return target;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class GroupRow {
    private String id;
    private String enterpriseId;
    private String enterpriseName;
    private String ownerId;
    private String name;
    private String status;
    private int memberCount;
    private Instant createdAt;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class FileRow {
    private String id;
    private String uploaderId;
    private String originalName;
    private String contentType;
    private long sizeBytes;
    private String status;
    private Instant createdAt;
    private String downloadUrl;
    private String previewUrl;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class FileTransferRow {
    private String id;
    private String fileId;
    private String userId;
    private String direction;
    private int progress;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
public static class FileCleanupResult {
    private int expiredFiles;
    private int physicalDeleted;
    private int retentionDays;
}

private static class StoredFileRef {
    private final String id;
    private final String objectKey;

    private StoredFileRef(String id, String objectKey) {
        this.id = id;
        this.objectKey = objectKey;
    }
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class FileStatusRequest {
    private String status;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class ConfirmRequest {
    private String confirmText;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class MessageAuditRow {
    private String id;
    private String conversationId;
    private String senderId;
    private String type;
    private String content;
    private String status;
    private Instant createdAt;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class CallAuditRow {
    private String id;
    private String conversationId;
    private String callerId;
    private String calleeId;
    private String groupId;
    private String mediaType;
    private String status;
    private Instant startedAt;
    private Instant answeredAt;
    private Instant endedAt;
    private String turnSessionId;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class MuteRequest {
    private boolean muted;
}
}
