package com.enterpriseim.server.feature;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.auth.UserAuthService;
import com.enterpriseim.server.user.UserService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api")
public class DirectoryController {
    private final JdbcTemplate jdbcTemplate;
    private final UserAuthService authService;
    private final UserService userService;

    public DirectoryController(JdbcTemplate jdbcTemplate, UserAuthService authService, UserService userService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.userService = userService;
    }

    @GetMapping("/directory/enterprises")
    public ApiResponse<List<EnterpriseDto>> enterprises(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return ApiResponse.ok(jdbcTemplate.query("SELECT id, name, code FROM enterprises ORDER BY created_at DESC, id ASC",
                (rs, rowNum) -> new EnterpriseDto(rs.getString("id"), rs.getString("name"), rs.getString("code"))));
    }

    @GetMapping("/directory/departments")
    public ApiResponse<List<DepartmentDto>> departments(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @RequestParam(required = false) String enterpriseId) {
        authService.requireUser(authorization);
        val sql = new StringBuilder("SELECT d.id, d.enterprise_id, d.parent_id, d.name, d.sort_order,\n" +
                "       (SELECT COUNT(*) FROM department_members dm WHERE dm.department_id = d.id) AS member_count\n" +
                "FROM departments d WHERE 1 = 1");
        val params = new ArrayList<Object>();
        if (hasText(enterpriseId)) {
            sql.append(" AND d.enterprise_id = ?");
            params.add(enterpriseId.trim());
        }
        sql.append(" ORDER BY d.sort_order ASC, d.created_at ASC, d.id ASC");
        return ApiResponse.ok(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DepartmentDto(
                rs.getString("id"),
                rs.getString("enterprise_id"),
                rs.getString("parent_id"),
                rs.getString("name"),
                rs.getInt("sort_order"),
                rs.getInt("member_count")
        ), params.toArray()));
    }

    @GetMapping("/directory/users")
    public ApiResponse<List<UserDto>> users(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestParam(required = false) String enterpriseId,
                                            @RequestParam(required = false) String departmentId,
                                            @RequestParam(required = false) String query,
                                            @RequestParam(defaultValue = "100") int limit) {
        val currentUserId = authService.requireUser(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 200));
        val sql = new StringBuilder("SELECT DISTINCT u.id, u.enterprise_id, u.phone, u.email, u.display_name, u.avatar_url, u.signature, u.status\n" +
                "FROM users u\n");
        val params = new ArrayList<Object>();
        if (hasText(departmentId)) {
            sql.append("JOIN department_members dm ON dm.user_id = u.id\n");
        }
        sql.append("WHERE u.status = 'active' AND u.id <> ?\n");
        params.add(currentUserId);
        if (hasText(enterpriseId)) {
            sql.append(" AND u.enterprise_id = ?");
            params.add(enterpriseId.trim());
        }
        if (hasText(departmentId)) {
            sql.append(" AND dm.department_id = ?");
            params.add(departmentId.trim());
        }
        if (hasText(query)) {
            sql.append(" AND (LOWER(u.display_name) LIKE ? OR u.phone LIKE ? OR LOWER(u.id) LIKE ?)");
            val pattern = "%" + query.trim().toLowerCase() + "%";
            params.add(pattern);
            params.add("%" + query.trim() + "%");
            params.add(pattern);
        }
        sql.append(" ORDER BY u.display_name ASC, u.id ASC LIMIT ?");
        params.add(boundedLimit);
        return ApiResponse.ok(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> userDto(rs), params.toArray()));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<UserDto> userDetail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @PathVariable String userId) {
        authService.requireUser(authorization);
        val rows = jdbcTemplate.query("SELECT id, enterprise_id, phone, email, display_name, avatar_url, signature, status\n" +
                "FROM users WHERE id = ? AND status = 'active'", (rs, rowNum) -> userDto(rs), userId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "用户未找到");
        }
        return ApiResponse.ok(rows.get(0));
    }

    @GetMapping("/friends")
    public ApiResponse<List<UserDto>> friends(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestParam String userId) {
        val currentUserId = authService.requireUser(authorization);
        authService.requireSameUser(currentUserId, userId);
        return ApiResponse.ok(jdbcTemplate.query("SELECT u.id, u.enterprise_id, u.phone, u.email, u.display_name, u.avatar_url, u.signature, u.status\n" +
                "FROM friendships f JOIN users u ON u.id = f.friend_id\n" +
                "WHERE f.user_id = ? AND u.status = 'active'\n" +
                "ORDER BY u.display_name ASC, u.id ASC", (rs, rowNum) -> userDto(rs), userId));
    }

    @GetMapping("/friend-requests")
    public ApiResponse<List<FriendRequestDto>> friendRequests(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                              @RequestParam String userId,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(defaultValue = "all") String box) {
        val currentUserId = authService.requireUser(authorization);
        authService.requireSameUser(currentUserId, userId);
        val sql = new StringBuilder("SELECT fr.id, fr.requester_id, requester.display_name AS requester_name,\n" +
                "       fr.receiver_id, receiver.display_name AS receiver_name, fr.message, fr.status, fr.created_at, fr.handled_at\n" +
                "FROM friend_requests fr\n" +
                "JOIN users requester ON requester.id = fr.requester_id\n" +
                "JOIN users receiver ON receiver.id = fr.receiver_id\n" +
                "WHERE 1 = 1");
        val params = new ArrayList<Object>();
        if ("incoming".equals(box)) {
            sql.append(" AND fr.receiver_id = ?");
            params.add(userId);
        } else if ("outgoing".equals(box)) {
            sql.append(" AND fr.requester_id = ?");
            params.add(userId);
        } else {
            sql.append(" AND (fr.receiver_id = ? OR fr.requester_id = ?)");
            params.add(userId);
            params.add(userId);
        }
        if (hasText(status)) {
            sql.append(" AND fr.status = ?");
            params.add(status.trim());
        }
        sql.append(" ORDER BY fr.created_at DESC, fr.id ASC");
        return ApiResponse.ok(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new FriendRequestDto(
                rs.getString("id"),
                rs.getString("requester_id"),
                rs.getString("requester_name"),
                rs.getString("receiver_id"),
                rs.getString("receiver_name"),
                rs.getString("message"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("handled_at"))
        ), params.toArray()));
    }

    @PostMapping("/friend-requests")
    @Transactional
    public ApiResponse<FriendRequestDto> createFriendRequest(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                             @RequestBody FriendCreateRequest request) {
        val requesterId = authService.requireUser(authorization);
        val receiverId = resolveReceiverId(request.receiverId(), request.receiverAccount());
        if (requesterId.equals(receiverId)) {
            throw new ResponseStatusException(BAD_REQUEST, "不能添加自己为好友");
        }
        rejectIfBlacklisted(requesterId, receiverId);
        if (exists("SELECT COUNT(*) FROM friendships WHERE user_id = ? AND friend_id = ?", requesterId, receiverId)) {
            throw new ResponseStatusException(BAD_REQUEST, "已经是好友");
        }
        val existing = latestPendingRequest(requesterId, receiverId);
        if (existing != null) {
            return ApiResponse.ok(friendRequestById(existing));
        }
        val id = "fr_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO friend_requests(id, requester_id, receiver_id, message, status) VALUES (?, ?, ?, ?, 'pending')",
                id, requesterId, receiverId, valueOr(request.message(), "friend request"));
        return ApiResponse.ok(friendRequestById(id));
    }

    @PostMapping("/friend-requests/{requestId}/handle")
    @Transactional
    public ApiResponse<FriendRequestDto> handleFriendRequest(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                             @PathVariable String requestId,
                                                             @RequestBody FriendHandleRequest request) {
        val currentUserId = authService.requireUser(authorization);
        val existing = friendRequestById(requestId);
        if (!currentUserId.equals(existing.receiverId())) {
            throw new ResponseStatusException(FORBIDDEN, "仅接收方可以处理好友申请");
        }
        val status = request.accept() ? "accepted" : "rejected";
        if (request.accept()) {
            rejectIfBlacklisted(existing.requesterId(), existing.receiverId());
        }
        jdbcTemplate.update("UPDATE friend_requests SET status = ?, handled_at = CURRENT_TIMESTAMP WHERE id = ?", status, requestId);
        if (request.accept()) {
            ensureFriendship(existing.requesterId(), existing.receiverId(), "friend_request");
            ensureFriendship(existing.receiverId(), existing.requesterId(), "friend_request");
        }
        return ApiResponse.ok(friendRequestById(requestId));
    }

    @GetMapping("/groups")
    public ApiResponse<List<GroupDto>> groups(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestParam String userId) {
        val currentUserId = authService.requireUser(authorization);
        authService.requireSameUser(currentUserId, userId);
        return ApiResponse.ok(jdbcTemplate.query("SELECT g.id, g.enterprise_id, g.owner_id, g.name, g.avatar_url, g.notice, g.status, g.created_at, g.join_approval_required,\n" +
                "       (SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = g.id) AS member_count\n" +
                "FROM group_members mine JOIN chat_groups g ON g.id = mine.group_id\n" +
                "WHERE mine.user_id = ? AND g.status = 'active'\n" +
                "ORDER BY g.updated_at DESC, g.created_at DESC", (rs, rowNum) -> groupDto(rs), userId));
    }

    @PostMapping("/groups")
    @Transactional
    public ApiResponse<GroupDto> createGroup(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestBody GroupCreateRequest request) {
        val ownerId = authService.requireUser(authorization);
        val ownerEnterpriseId = userEnterpriseId(ownerId);
        val maxOwnedGroups = intConfig("group.maxOwnedGroups", 100);
        val ownedGroups = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_groups WHERE owner_id = ? AND status = 'active'", Integer.class, ownerId);
        if (ownedGroups != null && ownedGroups >= maxOwnedGroups) {
            throw new ResponseStatusException(BAD_REQUEST, "建群数量超出限额");
        }
        val memberIds = uniqueMemberIds(request.memberIds(), ownerId);
        val maxMembers = intConfig("group.maxMembers", 500);
        if (memberIds.size() + 1 > maxMembers) {
            throw new ResponseStatusException(BAD_REQUEST, "群成员数量超出限额");
        }
        val id = "g_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO chat_groups(id, enterprise_id, owner_id, name, avatar_url, group_no, notice, status)\n" +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'active')", id, ownerEnterpriseId, ownerId,
                valueOr(request.name(), "New Group"), request.avatarUrl(), "GN" + System.currentTimeMillis(), request.notice());
        ensureGroupMember(id, ownerId, "owner");
        for (String memberId : memberIds) {
            userService.ensureUser(memberId, memberId, null);
            ensureGroupMember(id, memberId, "member");
        }
        ensureConversation(id, "group", id);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) SELECT ?, user_id FROM group_members WHERE group_id = ?", id, id);
        return ApiResponse.ok(groupById(id));
    }

    @PostMapping("/groups/{groupId}/members")
    @Transactional
    public ApiResponse<GroupDto> addGroupMember(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @PathVariable String groupId,
                                                @RequestBody GroupMemberRequest request) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        val maxMembers = intConfig("group.maxMembers", 500);
        val memberCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_members WHERE group_id = ?", Integer.class, groupId);
        if (memberCount != null && memberCount >= maxMembers) {
            throw new ResponseStatusException(BAD_REQUEST, "群成员数量超出限额");
        }
        userService.ensureUser(request.userId(), request.userId(), null);
        ensureGroupMember(groupId, request.userId(), "member");
        ensureConversation(groupId, "group", groupId);
        ensureConversationMember(groupId, request.userId());
        return ApiResponse.ok(groupById(groupId));
    }

    @PostMapping("/groups/{groupId}/members/batch")
    @Transactional
    public ApiResponse<GroupDto> batchAddGroupMembers(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @PathVariable String groupId,
                                                      @RequestBody GroupMemberBatchRequest request) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        val memberIds = uniqueMemberIds(request.userIds(), currentUserId);
        val maxMembers = intConfig("group.maxMembers", 500);
        val memberCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_members WHERE group_id = ?", Integer.class, groupId);
        if ((memberCount == null ? 0 : memberCount) + memberIds.size() > maxMembers) {
            throw new ResponseStatusException(BAD_REQUEST, "群成员数量超出限额");
        }
        for (String memberId : memberIds) {
            userService.ensureUser(memberId, memberId, null);
            ensureGroupMember(groupId, memberId, "member");
            ensureConversationMember(groupId, memberId);
        }
        return ApiResponse.ok(groupById(groupId));
    }

    @GetMapping("/groups/{groupId}/members/export")
    public ApiResponse<List<GroupMemberExportDto>> exportGroupMembers(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                      @PathVariable String groupId) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwnerOrMember(groupId, currentUserId);
        return ApiResponse.ok(jdbcTemplate.query("SELECT gm.user_id, u.display_name, u.phone, gm.role, gm.joined_at FROM group_members gm JOIN users u ON u.id = gm.user_id WHERE gm.group_id = ? ORDER BY gm.joined_at ASC",
                (rs, rowNum) -> new GroupMemberExportDto(rs.getString("user_id"), rs.getString("display_name"), rs.getString("phone"), rs.getString("role"), toInstant(rs.getTimestamp("joined_at"))),
                groupId));
    }

    @PatchMapping("/groups/{groupId}/approval")
    public ApiResponse<GroupDto> updateGroupApproval(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     @PathVariable String groupId,
                                                     @RequestBody GroupApprovalRequest request) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        jdbcTemplate.update("UPDATE chat_groups SET join_approval_required = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", request.enabled(), groupId);
        return ApiResponse.ok(groupById(groupId));
    }

    @PostMapping("/groups/{groupId}/invites")
    public ApiResponse<GroupInviteDto> createGroupInvite(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @PathVariable String groupId) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwnerOrMember(groupId, currentUserId);
        val token = "ginv_" + UUID.randomUUID();
        val expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        jdbcTemplate.update("INSERT INTO group_invites(token, group_id, inviter_id, expires_at) VALUES (?, ?, ?, ?)",
                token, groupId, currentUserId, Timestamp.from(expiresAt));
        return ApiResponse.ok(new GroupInviteDto(groupId, token, "/groups/" + groupId + "/join?token=" + token, expiresAt.toString()));
    }

    @PostMapping("/groups/{groupId}/join-requests")
    @Transactional
    public ApiResponse<GroupJoinRequestDto> requestJoinGroup(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @PathVariable String groupId,
                                                            @RequestBody GroupJoinRequestCreate request) {
        val requesterId = authService.requireUser(authorization);
        if (exists("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?", groupId, requesterId)) {
            throw new ResponseStatusException(BAD_REQUEST, "已经是群成员");
        }
        validateInvite(groupId, request.inviteToken());
        if (!groupApprovalRequired(groupId)) {
            ensureGroupMember(groupId, requesterId, "member");
            ensureConversationMember(groupId, requesterId);
            val id = "gjr_" + UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO group_join_requests(id, group_id, requester_id, invite_token, message, status, handled_at) VALUES (?, ?, ?, ?, ?, 'accepted', CURRENT_TIMESTAMP)",
                    id, groupId, requesterId, request.inviteToken(), request.message());
            return ApiResponse.ok(joinRequestById(id));
        }
        val id = "gjr_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO group_join_requests(id, group_id, requester_id, invite_token, message) VALUES (?, ?, ?, ?, ?)",
                id, groupId, requesterId, request.inviteToken(), request.message());
        return ApiResponse.ok(joinRequestById(id));
    }

    @GetMapping("/groups/{groupId}/join-requests")
    public ApiResponse<List<GroupJoinRequestDto>> listGroupJoinRequests(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                        @PathVariable String groupId,
                                                                        @RequestParam(defaultValue = "pending") String status) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        return ApiResponse.ok(jdbcTemplate.query("SELECT r.id, r.group_id, r.requester_id, u.display_name, r.message, r.status, r.created_at, r.handled_at FROM group_join_requests r JOIN users u ON u.id = r.requester_id WHERE r.group_id = ? AND r.status = ? ORDER BY r.created_at DESC",
                (rs, rowNum) -> joinRequestDto(rs), groupId, status));
    }

    @PostMapping("/groups/{groupId}/join-requests/{requestId}/handle")
    @Transactional
    public ApiResponse<GroupJoinRequestDto> handleGroupJoinRequest(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                  @PathVariable String groupId,
                                                                  @PathVariable String requestId,
                                                                  @RequestBody GroupJoinHandleRequest request) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        val item = joinRequestById(requestId);
        if (!groupId.equals(item.groupId())) throw new ResponseStatusException(BAD_REQUEST, "加群申请与群不匹配");
        if (!"pending".equals(item.status())) throw new ResponseStatusException(BAD_REQUEST, "加群申请已处理");
        val nextStatus = request.accept() ? "accepted" : "rejected";
        jdbcTemplate.update("UPDATE group_join_requests SET status = ?, handled_at = CURRENT_TIMESTAMP WHERE id = ?", nextStatus, requestId);
        if (request.accept()) {
            ensureGroupMember(groupId, item.requesterId(), "member");
            ensureConversationMember(groupId, item.requesterId());
        }
        return ApiResponse.ok(joinRequestById(requestId));
    }

    @DeleteMapping("/groups/{groupId}")
    @Transactional
    public ApiResponse<GroupDto> dissolveGroup(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @PathVariable String groupId) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        jdbcTemplate.update("UPDATE chat_groups SET status = 'dissolved', updated_at = CURRENT_TIMESTAMP WHERE id = ?", groupId);
        jdbcTemplate.update("DELETE FROM conversation_members WHERE conversation_id = ?", groupId);
        return ApiResponse.ok(groupById(groupId));
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    @Transactional
    public ApiResponse<GroupDto> removeGroupMember(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @PathVariable String groupId,
                                                   @PathVariable String userId) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwnerOrSelf(groupId, currentUserId, userId);
        jdbcTemplate.update("DELETE FROM group_members WHERE group_id = ? AND user_id = ? AND role <> 'owner'", groupId, userId);
        jdbcTemplate.update("DELETE FROM conversation_members WHERE conversation_id = ? AND user_id = ?", groupId, userId);
        return ApiResponse.ok(groupById(groupId));
    }

    @PatchMapping("/groups/{groupId}/notice")
    public ApiResponse<GroupDto> updateGroupNotice(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @PathVariable String groupId,
                                                   @RequestBody GroupNoticeRequest request) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        jdbcTemplate.update("UPDATE chat_groups SET notice = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", request.notice(), groupId);
        return ApiResponse.ok(groupById(groupId));
    }

    @PatchMapping("/groups/{groupId}/owner")
    @Transactional
    public ApiResponse<GroupDto> transferGroupOwner(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @PathVariable String groupId,
                                                    @RequestBody GroupOwnerRequest request) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        if (!exists("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?", groupId, request.ownerId())) {
            throw new ResponseStatusException(BAD_REQUEST, "新群主必须是群成员");
        }
        jdbcTemplate.update("UPDATE chat_groups SET owner_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", request.ownerId(), groupId);
        jdbcTemplate.update("UPDATE group_members SET role = 'member' WHERE group_id = ? AND role = 'owner'", groupId);
        jdbcTemplate.update("UPDATE group_members SET role = 'owner' WHERE group_id = ? AND user_id = ?", groupId, request.ownerId());
        return ApiResponse.ok(groupById(groupId));
    }

    @GetMapping("/friends/blacklist")
    public ApiResponse<List<Map<String, Object>>> listBlacklist(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                 @RequestParam String userId) {
        val currentUserId = authService.requireUser(authorization);
        authService.requireSameUser(currentUserId, userId);
        return ApiResponse.ok(jdbcTemplate.queryForList(
                "SELECT bl.user_id AS userId, bl.blocked_user_id AS blockedUserId, bl.created_at AS createdAt, u.display_name AS blockedName " +
                "FROM blacklists bl LEFT JOIN users u ON u.id = bl.blocked_user_id WHERE bl.user_id = ?", userId));
    }

    @PostMapping("/friends/blacklist")
    public ApiResponse<String> blockUser(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestBody Map<String, Object> body) {
        val currentUserId = authService.requireUser(authorization);
        val userId = (String) body.get("userId");
        val blockedUserId = (String) body.get("blockedUserId");
        authService.requireSameUser(currentUserId, userId);
        if (!exists("SELECT COUNT(*) FROM users WHERE id = ? AND status != 'disabled'", blockedUserId)) {
            throw new ResponseStatusException(BAD_REQUEST, "用户未找到");
        }
        if (!exists("SELECT COUNT(*) FROM blacklists WHERE user_id = ? AND blocked_user_id = ?", userId, blockedUserId)) {
            jdbcTemplate.update("INSERT INTO blacklists(user_id, blocked_user_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)", userId, blockedUserId);
        }
        return ApiResponse.ok("blocked");
    }

    @DeleteMapping("/friends/blacklist/{blockedUserId}")
    public ApiResponse<String> unblockUser(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestParam String userId,
                                            @PathVariable String blockedUserId) {
        val currentUserId = authService.requireUser(authorization);
        authService.requireSameUser(currentUserId, userId);
        jdbcTemplate.update("DELETE FROM blacklists WHERE user_id = ? AND blocked_user_id = ?", userId, blockedUserId);
        return ApiResponse.ok("unblocked");
    }

    @GetMapping("/files")
    public ApiResponse<List<FileDto>> files(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestParam String userId,
                                            @RequestParam(defaultValue = "100") int limit) {
        val currentUserId = authService.requireUser(authorization);
        authService.requireSameUser(currentUserId, userId);
        val boundedLimit = Math.max(1, Math.min(limit, 200));
        return ApiResponse.ok(jdbcTemplate.query("SELECT id, uploader_id, original_name, content_type, size_bytes, status, created_at\n" +
                "FROM files WHERE uploader_id = ? ORDER BY created_at DESC, id ASC LIMIT ?",
                (rs, rowNum) -> new FileDto(rs.getString("id"), rs.getString("uploader_id"), rs.getString("original_name"),
                        rs.getString("content_type"), rs.getLong("size_bytes"), rs.getString("status"), toInstant(rs.getTimestamp("created_at"))),
                userId, boundedLimit));
    }

    private String resolveReceiverId(String receiverId, String receiverAccount) {
        if (hasText(receiverId)) {
            userService.ensureUser(receiverId.trim(), receiverId.trim(), null);
            return receiverId.trim();
        }
        if (!hasText(receiverAccount)) {
            throw new ResponseStatusException(BAD_REQUEST, "接收方不能为空");
        }
        val account = receiverAccount.trim();
        val users = jdbcTemplate.queryForList("SELECT id FROM users WHERE id = ? OR phone = ? OR email = ? LIMIT 1", String.class, account, account, account);
        if (!users.isEmpty()) {
            return users.get(0);
        }
        val generatedId = account.matches("\\d+") ? "u_" + account : "u_" + account.replaceAll("[^A-Za-z0-9_\\-]", "_");
        userService.ensureUser(generatedId, account, account.matches("\\d+") ? account : null);
        return generatedId;
    }

    private String latestPendingRequest(String requesterId, String receiverId) {
        val ids = jdbcTemplate.queryForList("SELECT id FROM friend_requests\n" +
                "WHERE requester_id = ? AND receiver_id = ? AND status = 'pending'\n" +
                "ORDER BY created_at DESC LIMIT 1", String.class, requesterId, receiverId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private FriendRequestDto friendRequestById(String id) {
        return jdbcTemplate.query("SELECT fr.id, fr.requester_id, requester.display_name AS requester_name,\n" +
                "       fr.receiver_id, receiver.display_name AS receiver_name, fr.message, fr.status, fr.created_at, fr.handled_at\n" +
                "FROM friend_requests fr\n" +
                "JOIN users requester ON requester.id = fr.requester_id\n" +
                "JOIN users receiver ON receiver.id = fr.receiver_id\n" +
                "WHERE fr.id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "好友申请未找到");
            return new FriendRequestDto(rs.getString("id"), rs.getString("requester_id"), rs.getString("requester_name"),
                    rs.getString("receiver_id"), rs.getString("receiver_name"), rs.getString("message"), rs.getString("status"),
                    toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("handled_at")));
        }, id);
    }

    private GroupDto groupById(String id) {
        return jdbcTemplate.query("SELECT g.id, g.enterprise_id, g.owner_id, g.name, g.avatar_url, g.notice, g.status, g.created_at, g.join_approval_required,\n" +
                "       (SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = g.id) AS member_count\n" +
                "FROM chat_groups g WHERE g.id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "群聊未找到");
            return groupDto(rs);
        }, id);
    }

    private void ensureFriendship(String userId, String friendId, String source) {
        if (!exists("SELECT COUNT(*) FROM friendships WHERE user_id = ? AND friend_id = ?", userId, friendId)) {
            jdbcTemplate.update("INSERT INTO friendships(user_id, friend_id, source) VALUES (?, ?, ?)", userId, friendId, source);
        }
    }

    private void rejectIfBlacklisted(String requesterId, String receiverId) {
        if (exists("SELECT COUNT(*) FROM blacklists WHERE user_id = ? AND blocked_user_id = ?", requesterId, receiverId) ||
                exists("SELECT COUNT(*) FROM blacklists WHERE user_id = ? AND blocked_user_id = ?", receiverId, requesterId)) {
            throw new ResponseStatusException(FORBIDDEN, "已被黑名单拦截");
        }
    }

    private List<String> uniqueMemberIds(List<String> memberIds, String ownerId) {
        val result = new ArrayList<String>();
        if (memberIds == null) return result;
        for (String memberId : memberIds) {
            if (!hasText(memberId)) continue;
            val trimmed = memberId.trim();
            if (trimmed.equals(ownerId) || result.contains(trimmed)) continue;
            result.add(trimmed);
        }
        return result;
    }

    private int intConfig(String key, int fallback) {
        val values = jdbcTemplate.queryForList("SELECT config_value FROM system_configs WHERE config_key = ? LIMIT 1", String.class, key);
        if (values.isEmpty()) return fallback;
        try {
            return Integer.parseInt(values.get(0));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void ensureGroupMember(String groupId, String userId, String role) {
        if (exists("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?", groupId, userId)) {
            return;
        }
        jdbcTemplate.update("INSERT INTO group_members(group_id, user_id, role) VALUES (?, ?, ?)", groupId, userId, role);
    }

    private void ensureConversation(String id, String type, String targetId) {
        if (exists("SELECT COUNT(*) FROM conversations WHERE id = ?", id)) {
            return;
        }
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id) VALUES (?, ?, ?)", id, type, targetId);
    }

    private void ensureConversationMember(String conversationId, String userId) {
        if (exists("SELECT COUNT(*) FROM conversation_members WHERE conversation_id = ? AND user_id = ?", conversationId, userId)) {
            return;
        }
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, userId);
    }

    private boolean groupApprovalRequired(String groupId) {
        val values = jdbcTemplate.queryForList("SELECT join_approval_required FROM chat_groups WHERE id = ? LIMIT 1", Boolean.class, groupId);
        if (values.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "群聊未找到");
        return Boolean.TRUE.equals(values.get(0));
    }

    private void validateInvite(String groupId, String token) {
        if (!hasText(token)) return;
        if (!exists("SELECT COUNT(*) FROM group_invites WHERE group_id = ? AND token = ? AND expires_at > CURRENT_TIMESTAMP", groupId, token)) {
            throw new ResponseStatusException(BAD_REQUEST, "无效的群邀请");
        }
    }

    private GroupJoinRequestDto joinRequestById(String id) {
        return jdbcTemplate.query("SELECT r.id, r.group_id, r.requester_id, u.display_name, r.message, r.status, r.created_at, r.handled_at FROM group_join_requests r JOIN users u ON u.id = r.requester_id WHERE r.id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "加群申请未找到");
            return joinRequestDto(rs);
        }, id);
    }

    private GroupJoinRequestDto joinRequestDto(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new GroupJoinRequestDto(rs.getString("id"), rs.getString("group_id"), rs.getString("requester_id"), rs.getString("display_name"),
                rs.getString("message"), rs.getString("status"), toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("handled_at")));
    }

    private void requireGroupOwner(String groupId, String userId) {
        if (!exists("SELECT COUNT(*) FROM chat_groups WHERE id = ? AND owner_id = ?", groupId, userId)) {
            throw new ResponseStatusException(FORBIDDEN, "需要群主权限");
        }
    }

    private void requireGroupOwnerOrMember(String groupId, String userId) {
        if (!exists("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?", groupId, userId)) {
            throw new ResponseStatusException(FORBIDDEN, "需要是群成员");
        }
    }

    private void requireGroupOwnerOrSelf(String groupId, String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            return;
        }
        requireGroupOwner(groupId, currentUserId);
    }

    private String userEnterpriseId(String userId) {
        val ids = jdbcTemplate.queryForList("SELECT enterprise_id FROM users WHERE id = ? LIMIT 1", String.class, userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean exists(String sql, Object... params) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String valueOr(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private String toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private UserDto userDto(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserDto(rs.getString("id"), rs.getString("enterprise_id"), rs.getString("phone"), rs.getString("email"),
                rs.getString("display_name"), rs.getString("avatar_url"), rs.getString("signature"), rs.getString("status"));
    }

    private GroupDto groupDto(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new GroupDto(rs.getString("id"), rs.getString("enterprise_id"), rs.getString("owner_id"), rs.getString("name"),
                rs.getString("avatar_url"), rs.getString("notice"), rs.getString("status"), rs.getInt("member_count"),
                toInstant(rs.getTimestamp("created_at")), rs.getBoolean("join_approval_required"));
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class EnterpriseDto { private String id; private String name; private String code; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class DepartmentDto { private String id; private String enterpriseId; private String parentId; private String name; private int sortOrder; private int memberCount; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class UserDto { private String id; private String enterpriseId; private String phone; private String email; private String name; private String avatarUrl; private String signature; private String status; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FriendRequestDto { private String id; private String requesterId; private String requesterName; private String receiverId; private String receiverName; private String message; private String status; private String createdAt; private String handledAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupDto { private String id; private String enterpriseId; private String ownerId; private String name; private String avatarUrl; private String notice; private String status; private int memberCount; private String createdAt; private boolean joinApprovalRequired; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FileDto { private String id; private String uploaderId; private String originalName; private String contentType; private long sizeBytes; private String status; private String createdAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FriendCreateRequest { private String receiverId; private String receiverAccount; private String message; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FriendHandleRequest { private boolean accept; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupCreateRequest { private String name; private String avatarUrl; private String notice; private List<String> memberIds; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupMemberRequest { private String userId; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupMemberBatchRequest { private List<String> userIds; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupMemberExportDto { private String userId; private String name; private String phone; private String role; private String joinedAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupApprovalRequest { private boolean enabled; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupInviteDto { private String groupId; private String token; private String qrPayload; private String expiresAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupJoinRequestCreate { private String inviteToken; private String message; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupJoinHandleRequest { private boolean accept; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupJoinRequestDto { private String id; private String groupId; private String requesterId; private String requesterName; private String message; private String status; private String createdAt; private String handledAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupNoticeRequest { private String notice; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupOwnerRequest { private String ownerId; }
}
