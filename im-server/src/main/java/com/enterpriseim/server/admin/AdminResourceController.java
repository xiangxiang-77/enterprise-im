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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminResourceController {
    private final JdbcTemplate jdbcTemplate;
    private final AdminAuthService authService;
    private final CallService callService;

    public AdminResourceController(JdbcTemplate jdbcTemplate, AdminAuthService authService, CallService callService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.callService = callService;
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
                toInstant(rs.getTimestamp("created_at"))
        ), params.toArray()));
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
}
