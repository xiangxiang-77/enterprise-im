package com.enterpriseim.server.feature;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.auth.UserAuthService;
import com.enterpriseim.server.config.ImProperties;
import com.enterpriseim.server.ocr.BaiduOcrProvider;
import com.enterpriseim.server.ocr.OcrProvider;
import com.enterpriseim.server.preview.OfficePreviewProvider;
import com.enterpriseim.server.preview.OnlyOfficePreviewProvider;
import com.enterpriseim.server.tcp.OnlineSessionRegistry;
import com.enterpriseim.server.user.UserService;
import com.enterpriseim.server.ws.WebSocketSessionRegistry;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api")
public class ProductFeatureController {
    private static final Logger LOG = Logger.getLogger(ProductFeatureController.class.getName());

    private final JdbcTemplate jdbcTemplate;
    private final UserAuthService authService;
    private final UserService userService;
    private final OnlineSessionRegistry tcpSessions;
    private final WebSocketSessionRegistry wsSessions;
    private final ImProperties properties;
    private final NotificationService notificationService;
    private final OcrProvider ocrProvider;
    private final OfficePreviewProvider officePreviewProvider;

    public ProductFeatureController(JdbcTemplate jdbcTemplate, UserAuthService authService, UserService userService,
                                    OnlineSessionRegistry tcpSessions, WebSocketSessionRegistry wsSessions, ImProperties properties,
                                    NotificationService notificationService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.userService = userService;
        this.tcpSessions = tcpSessions;
        this.wsSessions = wsSessions;
        this.properties = properties;
        this.notificationService = notificationService;
        this.ocrProvider = createOcrProvider(objectMapper);
        this.officePreviewProvider = createOfficePreviewProvider();
    }

    private OcrProvider createOcrProvider(ObjectMapper objectMapper) {
        String apiKey = properties.getBaidu().getOcr().getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            LOG.info("Initializing Baidu OCR provider");
            return new BaiduOcrProvider(properties, objectMapper);
        }
        LOG.info("Baidu OCR not configured; OCR will be unavailable");
        return null;
    }

    private OfficePreviewProvider createOfficePreviewProvider() {
        String apiUrl = properties.getOnlyoffice().getApiUrl();
        if (apiUrl != null && !apiUrl.isEmpty()) {
            LOG.info("Initializing OnlyOffice preview provider");
            return new OnlyOfficePreviewProvider(properties);
        }
        LOG.info("OnlyOffice not configured; Office preview will be unavailable");
        return null;
    }

    @GetMapping("/workspace-apps")
    public ApiResponse<List<WorkspaceAppDto>> workspaceApps(@RequestHeader(value = "Authorization", required = false) String authorization) {
        val userId = authService.requireUser(authorization);
        return ApiResponse.ok(jdbcTemplate.query(
                "SELECT wa.id, wa.name, wa.icon, wa.url, wa.visible_department_id, wa.sort_order, wa.enabled\n" +
                        "FROM workspace_apps wa\n" +
                        "WHERE wa.enabled = TRUE\n" +
                        "  AND (wa.visible_department_id IS NULL OR wa.visible_department_id = '' OR EXISTS (\n" +
                        "      SELECT 1 FROM department_members dm WHERE dm.department_id = wa.visible_department_id AND dm.user_id = ?\n" +
                        "  ))\n" +
                        "ORDER BY wa.sort_order ASC, wa.name ASC",
                (rs, rowNum) -> new WorkspaceAppDto(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("icon"),
                        rs.getString("url"),
                        rs.getString("visible_department_id"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("enabled")
                ),
                userId));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @Transactional
    public ApiResponse<MessageDto> sendMessage(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @PathVariable String conversationId,
                                               @RequestBody MessageCreateRequest request) {
        val userId = authService.requireUser(authorization);
        if (!userId.equals(request.senderId())) throw new ResponseStatusException(FORBIDDEN, "发送者令牌不匹配");
        val blockedWord = firstBlockedWord(request.content());
        if (blockedWord != null) {
            insertRisk("sensitive_word", userId, conversationId, null, "blocked word: " + blockedWord);
            throw new ResponseStatusException(BAD_REQUEST, "消息被敏感词策略拦截");
        }
        ensureConversation(conversationId, request.conversationType(), request.targetId());
        enforceConversationSendPolicy(conversationId, userId);
        ensureMember(conversationId, userId);
        if (request.targetId() != null && request.conversationType() != null && request.conversationType().equals("single")) {
            userService.ensureUser(request.targetId(), request.targetId(), null);
            ensureMember(conversationId, request.targetId());
        }
        val fileId = request.fileId();
        if (fileId != null && !fileId.trim().isEmpty()) ensureFileExists(fileId);
        val id = "m_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, file_id, status, client_seq, expire_after_read)\n" +
                "VALUES (?, ?, ?, ?, ?, ?, 'sent', ?, ?)", id, conversationId, userId, valueOr(request.type(), "text"), request.content(), emptyToNull(fileId), request.clientSeq(), request.expireAfterRead());
        notificationService.notifyConversation(conversationId, userId, "message", id, request.content());
        return ApiResponse.ok(messageById(id));
    }

    @PatchMapping("/messages/{messageId}/edit")
    @Transactional
    public ApiResponse<MessageDto> editMessage(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @PathVariable String messageId,
                                               @RequestBody MessageEditRequest request) {
        val userId = authService.requireUser(authorization);
        val existing = messageById(messageId);
        if (!userId.equals(existing.senderId())) throw new ResponseStatusException(FORBIDDEN, "仅发送者可以编辑");
        ensureMessageEditable(existing);
        val blockedWord = firstBlockedWord(request.content());
        if (blockedWord != null) {
            insertRisk("sensitive_word", userId, existing.conversationId(), messageId, "blocked edit word: " + blockedWord);
            throw new ResponseStatusException(BAD_REQUEST, "消息编辑被敏感词策略拦截");
        }
        jdbcTemplate.update("INSERT INTO message_edits(id, message_id, editor_id, old_content, new_content) VALUES (?, ?, ?, ?, ?)",
                "edit_" + UUID.randomUUID(), messageId, userId, existing.content(), request.content());
        jdbcTemplate.update("UPDATE messages SET content = ?, edited_at = CURRENT_TIMESTAMP WHERE id = ?", request.content(), messageId);
        return ApiResponse.ok(messageById(messageId));
    }

    @PostMapping("/messages/{messageId}/recall")
    @Transactional
    public ApiResponse<MessageDto> recallMessage(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @PathVariable String messageId,
                                                 @RequestBody RecallRequest request) {
        val userId = authService.requireUser(authorization);
        val existing = messageById(messageId);
        if (!userId.equals(existing.senderId())) throw new ResponseStatusException(FORBIDDEN, "仅发送者可以撤回");
        ensureMessageWithinWindow(existing, "message recall window expired");
        ensureMessageActive(existing);
        jdbcTemplate.update("INSERT INTO message_recalls(id, message_id, operator_id, reason) VALUES (?, ?, ?, ?)",
                "recall_" + UUID.randomUUID(), messageId, userId, request.reason());
        jdbcTemplate.update("UPDATE messages SET status = 'recalled', recalled_at = CURRENT_TIMESTAMP, content = ? WHERE id = ?",
                "你撤回了一条消息", messageId);
        notificationService.notifyConversation(existing.conversationId(), userId, "recall", messageId, existing.content());
        return ApiResponse.ok(messageById(messageId));
    }

    @PostMapping("/messages/{messageId}/read")
    @Transactional
    public ApiResponse<ReceiptDto> readMessage(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @PathVariable String messageId) {
        val userId = authService.requireUser(authorization);
        if (exists("SELECT COUNT(*) FROM message_receipts WHERE message_id = ? AND user_id = ?", messageId, userId)) {
            jdbcTemplate.update("UPDATE message_receipts SET read_at = CURRENT_TIMESTAMP WHERE message_id = ? AND user_id = ?", messageId, userId);
        } else {
            jdbcTemplate.update("INSERT INTO message_receipts(message_id, user_id, delivered_at, read_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", messageId, userId);
        }
        val msg = messageById(messageId);
        if (msg.expireAfterRead()) {
            jdbcTemplate.update("UPDATE messages SET status = 'destroyed', destroyed_at = CURRENT_TIMESTAMP, content = ? WHERE id = ?", "阅后即焚消息已销毁", messageId);
        }
        return ApiResponse.ok(new ReceiptDto(messageId, userId, Instant.now().toString()));
    }

    @GetMapping("/messages/{messageId}/receipts")
    public ApiResponse<List<ReceiptDto>> listReceipts(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @PathVariable String messageId) {
        authService.requireUser(authorization);
        return ApiResponse.ok(jdbcTemplate.query("SELECT message_id, user_id, read_at FROM message_receipts WHERE message_id = ? ORDER BY read_at DESC",
                (rs, rowNum) -> new ReceiptDto(rs.getString("message_id"), rs.getString("user_id"), toInstant(rs.getTimestamp("read_at"))), messageId));
    }

    @GetMapping("/messages/{messageId}/read-status")
    public ApiResponse<MessageReadStatusDto> readStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @PathVariable String messageId) {
        val requesterId = authService.requireUser(authorization);
        val message = messageById(messageId);
        if (!exists("SELECT COUNT(*) FROM conversation_members WHERE conversation_id = ? AND user_id = ?", message.conversationId(), requesterId)) {
            throw new ResponseStatusException(FORBIDDEN, "不是该会话的成员");
        }
        val members = jdbcTemplate.query("SELECT cm.user_id, u.display_name, mr.read_at " +
                        "FROM conversation_members cm " +
                        "LEFT JOIN users u ON u.id = cm.user_id " +
                        "LEFT JOIN message_receipts mr ON mr.message_id = ? AND mr.user_id = cm.user_id " +
                        "WHERE cm.conversation_id = ? AND cm.user_id <> ? ORDER BY u.display_name, cm.user_id",
                (rs, rowNum) -> new ReadMemberDto(rs.getString("user_id"), rs.getString("display_name"), toInstant(rs.getTimestamp("read_at"))),
                messageId, message.conversationId(), message.senderId());
        val read = new ArrayList<ReadMemberDto>();
        val unread = new ArrayList<ReadMemberDto>();
        for (ReadMemberDto member : members) {
            if (member.readAt() == null) unread.add(member);
            else read.add(member);
        }
        return ApiResponse.ok(new MessageReadStatusDto(messageId, message.conversationId(), read, unread));
    }

    @PostMapping("/messages/{messageId}/reactions")
    public ApiResponse<ReactionDto> react(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @PathVariable String messageId,
                                          @RequestBody ReactionRequest request) {
        val userId = authService.requireUser(authorization);
        val reaction = valueOr(request.reaction(), "like");
        if (!exists("SELECT COUNT(*) FROM message_reactions WHERE message_id = ? AND user_id = ? AND reaction = ?", messageId, userId, reaction)) {
            jdbcTemplate.update("INSERT INTO message_reactions(message_id, user_id, reaction) VALUES (?, ?, ?)", messageId, userId, reaction);
        }
        return ApiResponse.ok(new ReactionDto(messageId, userId, reaction));
    }

    @PostMapping("/messages/{messageId}/favorite")
    public ApiResponse<FavoriteDto> favorite(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @PathVariable String messageId) {
        val userId = authService.requireUser(authorization);
        if (!exists("SELECT COUNT(*) FROM favorite_messages WHERE user_id = ? AND message_id = ?", userId, messageId)) {
            jdbcTemplate.update("INSERT INTO favorite_messages(user_id, message_id) VALUES (?, ?)", userId, messageId);
        }
        return ApiResponse.ok(new FavoriteDto(userId, messageId, true));
    }

    @DeleteMapping("/messages/{messageId}/favorite")
    public ApiResponse<FavoriteDto> unfavorite(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @PathVariable String messageId) {
        val userId = authService.requireUser(authorization);
        jdbcTemplate.update("DELETE FROM favorite_messages WHERE user_id = ? AND message_id = ?", userId, messageId);
        return ApiResponse.ok(new FavoriteDto(userId, messageId, false));
    }

    @PostMapping("/conversations/{conversationId}/screenshot")
    public ApiResponse<ScreenshotDto> screenshot(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @PathVariable String conversationId) {
        val userId = authService.requireUser(authorization);
        val id = "shot_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO screenshot_events(id, conversation_id, user_id) VALUES (?, ?, ?)", id, conversationId, userId);
        insertRisk("screenshot", userId, conversationId, null, "conversation screenshot captured");
        notificationService.notifyConversation(conversationId, userId, "screenshot", null, "conversation screenshot captured");
        return ApiResponse.ok(new ScreenshotDto(id, conversationId, userId, Instant.now().toString()));
    }

    // ---- Conversations ----

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationDto>> listConversations(@RequestHeader(value = "Authorization", required = false) String authorization) {
        val userId = authService.requireUser(authorization);
        val list = jdbcTemplate.query(
                "SELECT c.id, c.type, c.target_id, c.updated_at, " +
                "cm.muted, cm.pinned, cm.unread_count, cm.screenshot_notice, cm.recall_notice, cm.read_after_burn, " +
                "cm.strong_reminder, cm.display_member_nicknames, cm.saved_to_contacts, " +
                "(SELECT content FROM messages WHERE conversation_id = c.id ORDER BY created_at DESC LIMIT 1) as last_content, " +
                "(SELECT sender_id FROM messages WHERE conversation_id = c.id ORDER BY created_at DESC LIMIT 1) as last_sender, " +
                "(SELECT created_at FROM messages WHERE conversation_id = c.id ORDER BY created_at DESC LIMIT 1) as last_time, " +
                "(SELECT type FROM messages WHERE conversation_id = c.id ORDER BY created_at DESC LIMIT 1) as last_type " +
                "FROM conversation_members cm " +
                "JOIN conversations c ON c.id = cm.conversation_id " +
                "WHERE cm.user_id = ? " +
                "ORDER BY cm.pinned DESC, c.updated_at DESC",
                (rs, rowNum) -> new ConversationDto(
                        rs.getString("id"), rs.getString("type"), rs.getString("target_id"),
                        rs.getBoolean("muted"), rs.getBoolean("pinned"), rs.getInt("unread_count"),
                        rs.getString("last_content"), rs.getString("last_sender"),
                        toInstant(rs.getTimestamp("last_time")), rs.getString("last_type"),
                        toInstant(rs.getTimestamp("updated_at")),
                        rs.getBoolean("screenshot_notice"), rs.getBoolean("recall_notice"), rs.getBoolean("read_after_burn"),
                        rs.getBoolean("strong_reminder"), rs.getBoolean("display_member_nicknames"), rs.getBoolean("saved_to_contacts")
                ), userId);
        return ApiResponse.ok(list);
    }

    @PatchMapping("/conversations/{conversationId}/settings")
    public ApiResponse<ConversationSettingsDto> updateConversationSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String conversationId,
            @RequestBody ConversationSettingsRequest request) {
        val userId = authService.requireUser(authorization);
        if (!exists("SELECT COUNT(*) FROM conversation_members WHERE conversation_id = ? AND user_id = ?", conversationId, userId)) {
            throw new ResponseStatusException(FORBIDDEN, "不是该会话的成员");
        }
        if (request.muted() != null) {
            jdbcTemplate.update("UPDATE conversation_members SET muted = ? WHERE conversation_id = ? AND user_id = ?", request.muted(), conversationId, userId);
        }
        if (request.pinned() != null) {
            jdbcTemplate.update("UPDATE conversation_members SET pinned = ? WHERE conversation_id = ? AND user_id = ?", request.pinned(), conversationId, userId);
        }
        if (request.screenshotNotice() != null) {
            jdbcTemplate.update("UPDATE conversation_members SET screenshot_notice = ? WHERE conversation_id = ? AND user_id = ?", request.screenshotNotice(), conversationId, userId);
        }
        if (request.recallNotice() != null) {
            jdbcTemplate.update("UPDATE conversation_members SET recall_notice = ? WHERE conversation_id = ? AND user_id = ?", request.recallNotice(), conversationId, userId);
        }
        if (request.readAfterBurn() != null) {
            jdbcTemplate.update("UPDATE conversation_members SET read_after_burn = ? WHERE conversation_id = ? AND user_id = ?", request.readAfterBurn(), conversationId, userId);
        }
        if (request.strongReminder() != null) {
            jdbcTemplate.update("UPDATE conversation_members SET strong_reminder = ? WHERE conversation_id = ? AND user_id = ?", request.strongReminder(), conversationId, userId);
        }
        if (request.displayMemberNicknames() != null) {
            jdbcTemplate.update("UPDATE conversation_members SET display_member_nicknames = ? WHERE conversation_id = ? AND user_id = ?", request.displayMemberNicknames(), conversationId, userId);
        }
        if (request.savedToContacts() != null) {
            jdbcTemplate.update("UPDATE conversation_members SET saved_to_contacts = ? WHERE conversation_id = ? AND user_id = ?", request.savedToContacts(), conversationId, userId);
        }
        return ApiResponse.ok(conversationSettings(conversationId, userId));
    }

    @GetMapping("/notification-settings")
    public ApiResponse<NotificationSettingsDto> notificationSettings(@RequestHeader(value = "Authorization", required = false) String authorization) {
        val userId = authService.requireUser(authorization);
        ensureNotificationSettings(userId);
        return ApiResponse.ok(notificationSettingsByUser(userId));
    }

    @PatchMapping("/notification-settings")
    public ApiResponse<NotificationSettingsDto> updateNotificationSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody NotificationSettingsRequest request) {
        val userId = authService.requireUser(authorization);
        ensureNotificationSettings(userId);
        if (request.newMessage() != null) jdbcTemplate.update("UPDATE user_notification_settings SET new_message = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.newMessage(), userId);
        if (request.calls() != null) jdbcTemplate.update("UPDATE user_notification_settings SET calls = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.calls(), userId);
        if (request.detail() != null) jdbcTemplate.update("UPDATE user_notification_settings SET detail = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.detail(), userId);
        if (request.sound() != null) jdbcTemplate.update("UPDATE user_notification_settings SET sound = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.sound(), userId);
        if (request.vibration() != null) jdbcTemplate.update("UPDATE user_notification_settings SET vibration = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.vibration(), userId);
        if (request.screenshotNotice() != null) jdbcTemplate.update("UPDATE user_notification_settings SET screenshot_notice = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.screenshotNotice(), userId);
        if (request.recallNotice() != null) jdbcTemplate.update("UPDATE user_notification_settings SET recall_notice = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.recallNotice(), userId);
        if (request.mentionAlert() != null) jdbcTemplate.update("UPDATE user_notification_settings SET mention_alert = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.mentionAlert(), userId);
        if (request.dndEnabled() != null) jdbcTemplate.update("UPDATE user_notification_settings SET dnd_enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.dndEnabled(), userId);
        if (hasText(request.dndStart())) jdbcTemplate.update("UPDATE user_notification_settings SET dnd_start = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.dndStart(), userId);
        if (hasText(request.dndEnd())) jdbcTemplate.update("UPDATE user_notification_settings SET dnd_end = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", request.dndEnd(), userId);
        return ApiResponse.ok(notificationSettingsByUser(userId));
    }

    @GetMapping("/push/providers")
    public ApiResponse<PushProvidersDto> pushProviders(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return ApiResponse.ok(new PushProvidersDto(
                new PushProviderDto("apns", !"disabled".equals(config("push.apns.provider", "disabled")), config("push.apns.provider", "disabled")),
                new PushProviderDto("fcm", !"disabled".equals(config("push.fcm.provider", "disabled")), config("push.fcm.provider", "disabled")),
                new PushProviderDto("vendor", !"disabled".equals(config("push.vendor.provider", "disabled")), config("push.vendor.provider", "disabled"))
        ));
    }

    @GetMapping("/push/device-tokens")
    public ApiResponse<List<PushDeviceTokenDto>> pushDeviceTokens(@RequestHeader(value = "Authorization", required = false) String authorization) {
        val userId = authService.requireUser(authorization);
        return ApiResponse.ok(jdbcTemplate.query("SELECT id, user_id, platform, provider, token, enabled, updated_at FROM push_device_tokens WHERE user_id = ? ORDER BY updated_at DESC",
                (rs, rowNum) -> new PushDeviceTokenDto(rs.getString("id"), rs.getString("user_id"), rs.getString("platform"), rs.getString("provider"), maskToken(rs.getString("token")), rs.getBoolean("enabled"), toInstant(rs.getTimestamp("updated_at"))),
                userId));
    }

    @PostMapping("/push/device-tokens")
    public ApiResponse<PushDeviceTokenDto> registerPushDeviceToken(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                   @RequestBody PushDeviceTokenRequest request) {
        val userId = authService.requireUser(authorization);
        val provider = valueOr(request.provider(), "vendor").toLowerCase();
        if (!provider.equals("apns") && !provider.equals("fcm") && !provider.equals("vendor")) throw new ResponseStatusException(BAD_REQUEST, "推送服务商必须为 apns、fcm 或 vendor");
        if (!hasText(request.token())) throw new ResponseStatusException(BAD_REQUEST, "设备令牌不能为空");
        val id = "pdt_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO push_device_tokens(id, user_id, platform, provider, token) VALUES (?, ?, ?, ?, ?)",
                id, userId, valueOr(request.platform(), "web"), provider, request.token());
        return ApiResponse.ok(pushDeviceTokenById(id));
    }

    @DeleteMapping("/push/device-tokens/{tokenId}")
    public ApiResponse<PushDeviceTokenDto> disablePushDeviceToken(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                  @PathVariable String tokenId) {
        val userId = authService.requireUser(authorization);
        jdbcTemplate.update("UPDATE push_device_tokens SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?", tokenId, userId);
        return ApiResponse.ok(pushDeviceTokenById(tokenId));
    }

    // ---- Message forwarding ----

    @PostMapping("/messages/{messageId}/forward")
    @Transactional
    public ApiResponse<MessageDto> forwardMessage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String messageId,
            @RequestBody ForwardRequest request) {
        val userId = authService.requireUser(authorization);
        val original = messageById(messageId);
        val targetConv = request.targetConversationId();
        ensureMember(targetConv, userId);
        val id = "m_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, file_id, status, client_seq) VALUES (?, ?, ?, ?, ?, ?, 'sent', ?)",
                id, targetConv, userId, original.type(), original.content(), emptyToNull(original.fileId()), "fwd_" + id);
        jdbcTemplate.update("UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", targetConv);
        return ApiResponse.ok(messageById(id));
    }

    @PostMapping("/messages/forward")
    @Transactional
    public ApiResponse<List<MessageDto>> forwardMessages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ForwardBatchRequest request) {
        val userId = authService.requireUser(authorization);
        val messageIds = uniqueNonBlank(request.messageIds());
        val targetConversationIds = uniqueNonBlank(request.targetConversationIds());
        if (messageIds.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "messageIds 不能为空");
        if (targetConversationIds.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "targetConversationIds 不能为空");
        if (messageIds.size() > 100) throw new ResponseStatusException(BAD_REQUEST, "转发消息数量超过 100 条限制");
        for (String targetConv : targetConversationIds) {
            ensureMember(targetConv, userId);
        }
        val created = new ArrayList<MessageDto>();
        if ("combine".equals(valueOr(request.mode(), "single"))) {
            val originals = new ArrayList<MessageDto>();
            for (String messageId : messageIds) originals.add(messageById(messageId));
            val content = combinedForwardContent(originals);
            for (String targetConv : targetConversationIds) {
                val id = "m_" + UUID.randomUUID();
                jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, status, client_seq) VALUES (?, ?, ?, 'record', ?, 'sent', ?)",
                        id, targetConv, userId, content, "fwd_" + id);
                jdbcTemplate.update("UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", targetConv);
                created.add(messageById(id));
            }
        } else {
            for (String targetConv : targetConversationIds) {
                for (String messageId : messageIds) {
                    val original = messageById(messageId);
                    val id = "m_" + UUID.randomUUID();
                    jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, file_id, status, client_seq) VALUES (?, ?, ?, ?, ?, ?, 'sent', ?)",
                            id, targetConv, userId, original.type(), original.content(), emptyToNull(original.fileId()), "fwd_" + id);
                    created.add(messageById(id));
                }
                jdbcTemplate.update("UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", targetConv);
            }
        }
        return ApiResponse.ok(created);
    }

    // ---- Global search ----

    @GetMapping("/search")
    public ApiResponse<SearchResultDto> search(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String q,
            @RequestParam(defaultValue = "all") String type) {
        val userId = authService.requireUser(authorization);
        val like = "%" + q + "%";
        val result = new SearchResultDto();
        if (type.equals("all") || type.equals("contacts")) {
            result.contacts(jdbcTemplate.query("SELECT id, display_name, phone FROM users WHERE display_name LIKE ? OR phone LIKE ? LIMIT 20",
                    (rs, rowNum) -> new SearchUserItem(rs.getString("id"), rs.getString("display_name"), rs.getString("phone")), like, like));
        }
        if (type.equals("all") || type.equals("groups")) {
            result.groups(jdbcTemplate.query("SELECT g.id, g.name, g.notice FROM chat_groups g JOIN group_members gm ON gm.group_id = g.id WHERE gm.user_id = ? AND g.name LIKE ? LIMIT 20",
                    (rs, rowNum) -> new SearchGroupItem(rs.getString("id"), rs.getString("name"), rs.getString("notice"), "/chat/" + rs.getString("id")), userId, like));
        }
        if (type.equals("all") || type.equals("messages")) {
            result.messages(jdbcTemplate.query("SELECT m.id, m.conversation_id, m.sender_id, m.type, m.content, m.created_at FROM messages m JOIN conversation_members cm ON cm.conversation_id = m.conversation_id WHERE cm.user_id = ? AND (m.content LIKE ? OR m.type LIKE ?) ORDER BY m.created_at DESC LIMIT 50",
                    (rs, rowNum) -> new SearchMessageItem(rs.getString("id"), rs.getString("conversation_id"), rs.getString("sender_id"), rs.getString("type"), rs.getString("content"), toInstant(rs.getTimestamp("created_at")), "/chat/" + rs.getString("conversation_id") + "?messageId=" + rs.getString("id")), userId, like, like));
        }
        if (type.equals("all") || type.equals("files")) {
            result.files(jdbcTemplate.query("SELECT DISTINCT f.id, f.original_name, f.content_type, f.size_bytes FROM files f LEFT JOIN messages m ON m.file_id = f.id LEFT JOIN conversation_members cm ON cm.conversation_id = m.conversation_id WHERE (f.uploader_id = ? OR cm.user_id = ?) AND f.original_name LIKE ? LIMIT 20",
                    (rs, rowNum) -> new SearchFileItem(rs.getString("id"), rs.getString("original_name"), rs.getString("content_type"), rs.getLong("size_bytes"), "/files?fileId=" + rs.getString("id")), userId, userId, like));
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/search/recommendations")
    public ApiResponse<SearchRecommendationDto> searchRecommendations(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        val userId = authService.requireUser(authorization);
        val result = new SearchRecommendationDto();
        result.contacts(jdbcTemplate.query("SELECT u.id, u.display_name, u.phone FROM users u JOIN friendships f ON f.friend_id = u.id WHERE f.user_id = ? ORDER BY f.created_at DESC LIMIT 5",
                (rs, rowNum) -> new SearchUserItem(rs.getString("id"), rs.getString("display_name"), rs.getString("phone")), userId));
        result.groups(jdbcTemplate.query("SELECT g.id, g.name, g.notice FROM chat_groups g JOIN group_members gm ON gm.group_id = g.id WHERE gm.user_id = ? ORDER BY gm.joined_at DESC LIMIT 5",
                (rs, rowNum) -> new SearchGroupItem(rs.getString("id"), rs.getString("name"), rs.getString("notice"), "/chat/" + rs.getString("id")), userId));
        result.messages(jdbcTemplate.query("SELECT m.id, m.conversation_id, m.sender_id, m.type, m.content, m.created_at FROM messages m JOIN conversation_members cm ON cm.conversation_id = m.conversation_id WHERE cm.user_id = ? ORDER BY m.created_at DESC LIMIT 5",
                (rs, rowNum) -> new SearchMessageItem(rs.getString("id"), rs.getString("conversation_id"), rs.getString("sender_id"), rs.getString("type"), rs.getString("content"), toInstant(rs.getTimestamp("created_at")), "/chat/" + rs.getString("conversation_id") + "?messageId=" + rs.getString("id")), userId));
        return ApiResponse.ok(result);
    }

    // ---- Group member management ----

    @GetMapping("/groups/{groupId}/members")
    public ApiResponse<List<GroupMemberDto>> listGroupMembers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String groupId) {
        authService.requireUser(authorization);
        val list = jdbcTemplate.query(
                "SELECT gm.user_id, gm.role, gm.alias, gm.muted, gm.joined_at, u.display_name as user_name " +
                "FROM group_members gm LEFT JOIN users u ON u.id = gm.user_id WHERE gm.group_id = ? ORDER BY gm.role DESC, gm.joined_at ASC",
                (rs, rowNum) -> new GroupMemberDto(rs.getString("user_id"), rs.getString("user_name"), rs.getString("role"), rs.getString("alias"), rs.getBoolean("muted"), toInstant(rs.getTimestamp("joined_at"))),
                groupId);
        return ApiResponse.ok(list);
    }

    @PatchMapping("/groups/{groupId}/members/{userId}/mute")
    public ApiResponse<String> muteGroupMember(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String groupId,
            @PathVariable String userId,
            @RequestBody MuteRequest request) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        jdbcTemplate.update("UPDATE group_members SET muted = ? WHERE group_id = ? AND user_id = ?", request.muted(), groupId, userId);
        return ApiResponse.ok("ok");
    }

    // ---- Group operations ----

    @PatchMapping("/groups/{groupId}")
    public ApiResponse<String> updateGroup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String groupId,
            @RequestBody GroupUpdateRequest request) {
        val currentUserId = authService.requireUser(authorization);
        requireGroupOwner(groupId, currentUserId);
        if (request.name() != null) {
            jdbcTemplate.update("UPDATE chat_groups SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", request.name(), groupId);
        }
        if (request.notice() != null) {
            jdbcTemplate.update("UPDATE chat_groups SET notice = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", request.notice(), groupId);
        }
        if (request.avatarUrl() != null) {
            jdbcTemplate.update("UPDATE chat_groups SET avatar_url = ? WHERE id = ?", request.avatarUrl(), groupId);
        }
        return ApiResponse.ok("ok");
    }

    // ---- Online status ----

    @GetMapping("/users/online-status")
    public ApiResponse<Map<String, Boolean>> onlineStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String userIds) {
        authService.requireUser(authorization);
        val result = new java.util.HashMap<String, Boolean>();
        for (val uid : userIds.split(",")) {
            val trimmed = uid.trim();
            if (!trimmed.isEmpty()) {
                result.put(trimmed, tcpSessions.isOnline(trimmed) || wsSessions.isOnline(trimmed));
            }
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/files")
    public ApiResponse<FileDto> createFile(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestBody FileCreateRequest request) {
        val userId = authService.requireUser(authorization);
        if (!userId.equals(request.uploaderId())) throw new ResponseStatusException(FORBIDDEN, "上传者令牌不匹配");
        validateFilePolicy(request.originalName(), request.contentType(), request.sizeBytes());
        val id = "file_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO files(id, uploader_id, object_key, original_name, content_type, size_bytes, status) VALUES (?, ?, ?, ?, ?, ?, 'available')",
                id, userId, valueOr(request.objectKey(), id), request.originalName(), request.contentType(), request.sizeBytes());
        return ApiResponse.ok(fileById(id));
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileDto> uploadFile(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestParam String uploaderId,
                                           @RequestParam("file") MultipartFile file) {
        val userId = authService.requireUser(authorization);
        if (!userId.equals(uploaderId)) throw new ResponseStatusException(FORBIDDEN, "上传者令牌不匹配");
        if (file.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "文件为空");
        enforceTransferRate(userId, "upload", "max_uploads_per_minute", "max_upload_mb_per_minute", file.getSize());
        validateFilePolicy(file.getOriginalFilename(), file.getContentType(), file.getSize());
        val id = "file_" + UUID.randomUUID();
        val objectKey = userId + "/" + id + "/" + safeFileName(file.getOriginalFilename());
        try {
            val target = storagePath(objectKey);
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "文件上传失败");
        }
        jdbcTemplate.update("INSERT INTO files(id, uploader_id, object_key, original_name, content_type, size_bytes, status) VALUES (?, ?, ?, ?, ?, ?, 'available')",
                id, userId, objectKey, safeFileName(file.getOriginalFilename()), valueOr(file.getContentType(), "application/octet-stream"), file.getSize());
        val transferId = "ft_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO file_transfer_logs(id, file_id, user_id, direction, progress, status, size_bytes) VALUES (?, ?, ?, 'upload', 100, 'completed', ?)",
                transferId, id, userId, file.getSize());
        return ApiResponse.ok(fileById(id));
    }

    @PostMapping("/files/chunk-upload/sessions")
    public ApiResponse<FileUploadSessionDto> createChunkUploadSession(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                      @RequestBody FileUploadSessionRequest request) {
        val userId = authService.requireUser(authorization);
        if (!userId.equals(request.uploaderId())) throw new ResponseStatusException(FORBIDDEN, "上传者令牌不匹配");
        validateFilePolicy(request.originalName(), request.contentType(), request.totalSize());
        val id = "fus_" + UUID.randomUUID();
        val objectKey = userId + "/" + id + "/" + safeFileName(request.originalName());
        jdbcTemplate.update("INSERT INTO file_upload_sessions(id, uploader_id, original_name, content_type, total_size, total_chunks, object_key) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, userId, safeFileName(request.originalName()), valueOr(request.contentType(), "application/octet-stream"), request.totalSize(), Math.max(1, request.totalChunks()), objectKey);
        return ApiResponse.ok(uploadSessionById(id));
    }

    @PostMapping(value = "/files/chunk-upload/sessions/{sessionId}/chunks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadSessionDto> uploadChunk(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @PathVariable String sessionId,
                                                        @RequestParam int chunkIndex,
                                                        @RequestParam("file") MultipartFile file) {
        val userId = authService.requireUser(authorization);
        val session = uploadSessionRecord(sessionId);
        if (!userId.equals(session.uploaderId())) throw new ResponseStatusException(FORBIDDEN, "上传者令牌不匹配");
        if (chunkIndex < 0 || chunkIndex >= session.totalChunks()) throw new ResponseStatusException(BAD_REQUEST, "无效的分块序号");
        try {
            val chunkPath = storagePath(session.objectKey() + ".part/" + chunkIndex);
            Files.createDirectories(chunkPath.getParent());
            file.transferTo(chunkPath.toFile());
        } catch (IOException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "分块上传失败");
        }
        jdbcTemplate.update("DELETE FROM file_upload_chunks WHERE session_id = ? AND chunk_index = ?", sessionId, chunkIndex);
        jdbcTemplate.update("INSERT INTO file_upload_chunks(session_id, chunk_index, size_bytes) VALUES (?, ?, ?)", sessionId, chunkIndex, file.getSize());
        return ApiResponse.ok(uploadSessionById(sessionId));
    }

    @PostMapping("/files/chunk-upload/sessions/{sessionId}/complete")
    public ApiResponse<FileDto> completeChunkUpload(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @PathVariable String sessionId) {
        val userId = authService.requireUser(authorization);
        val session = uploadSessionRecord(sessionId);
        if (!userId.equals(session.uploaderId())) throw new ResponseStatusException(FORBIDDEN, "上传者令牌不匹配");
        val uploaded = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_upload_chunks WHERE session_id = ?", Integer.class, sessionId);
        if (uploaded == null || uploaded != session.totalChunks()) throw new ResponseStatusException(BAD_REQUEST, "分块未完成");
        val fileId = "file_" + UUID.randomUUID();
        try {
            val target = storagePath(session.objectKey());
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(target);
            for (int i = 0; i < session.totalChunks(); i++) {
                val chunk = storagePath(session.objectKey() + ".part/" + i);
                if (!Files.exists(chunk)) throw new ResponseStatusException(BAD_REQUEST, "分块缺失");
                Files.write(target, Files.readAllBytes(chunk), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "分块合并失败");
        }
        jdbcTemplate.update("INSERT INTO files(id, uploader_id, object_key, original_name, content_type, size_bytes, status) VALUES (?, ?, ?, ?, ?, ?, 'available')",
                fileId, userId, session.objectKey(), session.originalName(), session.contentType(), session.totalSize());
        jdbcTemplate.update("UPDATE file_upload_sessions SET status = 'completed', completed_at = CURRENT_TIMESTAMP WHERE id = ?", sessionId);
        insertTransferLog(fileId, userId, "upload", 100, "completed", session.totalSize());
        return ApiResponse.ok(fileById(fileId));
    }

    @GetMapping("/files/{fileId}/office-preview")
    public ApiResponse<FilePreviewAdapterDto> officePreview(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @PathVariable String fileId) {
        authService.requireUser(authorization);
        val record = fileRecord(fileId);
        val office = Arrays.asList("doc", "docx", "xls", "xlsx", "ppt", "pptx").contains(extension(record.originalName()));
        if (!office) throw new ResponseStatusException(BAD_REQUEST, "不需要 Office 预览");
        if (officePreviewProvider == null) {
            return ApiResponse.ok(new FilePreviewAdapterDto(fileId, "disabled", false, "office preview provider not configured", null, null));
        }
        try {
            String fileUrl = "/api/files/" + fileId + "/download";
            String fileType = extension(record.originalName());
            String previewUrl = officePreviewProvider.getPreviewUrl(fileUrl, fileType);
            if (previewUrl != null && !previewUrl.isEmpty()) {
                return ApiResponse.ok(new FilePreviewAdapterDto(fileId, officePreviewProvider.name(), true, "Office preview URL generated", null, previewUrl));
            } else {
                return ApiResponse.ok(new FilePreviewAdapterDto(fileId, officePreviewProvider.name(), false, "Failed to generate preview URL", null, null));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Office preview error for file: " + fileId, e);
            return ApiResponse.ok(new FilePreviewAdapterDto(fileId, officePreviewProvider.name(), false, "Office preview error: " + e.getMessage(), null, null));
        }
    }

    @GetMapping("/files/{fileId}/ocr")
    public ApiResponse<FilePreviewAdapterDto> ocrPreview(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                         @PathVariable String fileId) {
        authService.requireUser(authorization);
        val record = fileRecord(fileId);
        if (!isOcrCandidate(record.contentType(), record.originalName())) {
            throw new ResponseStatusException(BAD_REQUEST, "此文件不支持 OCR");
        }
        if (ocrProvider == null) {
            return ApiResponse.ok(new FilePreviewAdapterDto(fileId, "disabled", false, "ocr provider not configured", null, null));
        }
        try {
            val path = storagePath(record.objectKey());
            if (!Files.exists(path)) throw new ResponseStatusException(BAD_REQUEST, "文件内容未找到");
            byte[] imageBytes = Files.readAllBytes(path);
            String format = extension(record.originalName());
            String result = ocrProvider.recognize(imageBytes, format);
            if (result != null && !result.isEmpty()) {
                return ApiResponse.ok(new FilePreviewAdapterDto(fileId, ocrProvider.name(), true, "OCR completed", result, null));
            } else {
                return ApiResponse.ok(new FilePreviewAdapterDto(fileId, ocrProvider.name(), false, "OCR returned no text", null, null));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "OCR processing error for file: " + fileId, e);
            return ApiResponse.ok(new FilePreviewAdapterDto(fileId, ocrProvider.name(), false, "OCR error: " + e.getMessage(), null, null));
        }
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @PathVariable String fileId) {
        val userId = authService.requireUser(authorization);
        val record = fileRecord(fileId);
        enforceTransferRate(userId, "download", "max_downloads_per_minute", "max_download_mb_per_minute", record.sizeBytes());
        val path = storagePath(record.objectKey());
        if (!Files.exists(path)) throw new ResponseStatusException(BAD_REQUEST, "文件内容未找到");
        insertTransferLog(fileId, userId, "download", 100, "completed", record.sizeBytes());
        val encodedName = URLEncoder.encode(record.originalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(mediaType(record.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new FileSystemResource(path));
    }

    @GetMapping("/files/{fileId}/preview")
    public ResponseEntity<Resource> previewFile(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @PathVariable String fileId) {
        val userId = authService.requireUser(authorization);
        val record = fileRecord(fileId);
        enforceTransferRate(userId, "preview", "max_downloads_per_minute", "max_download_mb_per_minute", record.sizeBytes());
        if (!isPreviewable(record.contentType(), record.originalName())) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持此文件预览");
        }
        val path = storagePath(record.objectKey());
        if (!Files.exists(path)) throw new ResponseStatusException(BAD_REQUEST, "文件内容未找到");
        insertTransferLog(fileId, userId, "preview", 100, "completed", record.sizeBytes());
        return ResponseEntity.ok()
                .contentType(mediaType(record.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new FileSystemResource(path));
    }

    @PostMapping("/files/{fileId}/transfers")
    public ApiResponse<FileTransferDto> transfer(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @PathVariable String fileId,
                                                 @RequestBody FileTransferRequest request) {
        val userId = authService.requireUser(authorization);
        val id = "ft_" + UUID.randomUUID();
        val file = fileRecord(fileId);
        jdbcTemplate.update("INSERT INTO file_transfer_logs(id, file_id, user_id, direction, progress, status, size_bytes) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, fileId, userId, valueOr(request.direction(), "download"), clampProgress(request.progress()), valueOr(request.status(), "pending"), file.sizeBytes());
        return ApiResponse.ok(transferById(id));
    }

    private MessageDto messageById(String id) {
        return jdbcTemplate.query("SELECT id, conversation_id, sender_id, type, content, file_id, status, client_seq, created_at, edited_at, recalled_at, expire_after_read, destroyed_at FROM messages WHERE id = ?",
                rs -> {
                    if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "消息未找到");
                    return new MessageDto(rs.getString("id"), rs.getString("conversation_id"), rs.getString("sender_id"), rs.getString("type"),
                            rs.getString("content"), rs.getString("file_id"), rs.getString("status"), rs.getString("client_seq"),
                            toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("edited_at")), toInstant(rs.getTimestamp("recalled_at")),
                            rs.getBoolean("expire_after_read"), toInstant(rs.getTimestamp("destroyed_at")));
                }, id);
    }

    private FileDto fileById(String id) {
        return jdbcTemplate.query("SELECT id, uploader_id, original_name, content_type, size_bytes, status, created_at FROM files WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "文件未找到");
            return new FileDto(rs.getString("id"), rs.getString("uploader_id"), rs.getString("original_name"), rs.getString("content_type"),
                    rs.getLong("size_bytes"), rs.getString("status"), toInstant(rs.getTimestamp("created_at")),
                    "/api/files/" + rs.getString("id") + "/download", previewUrl(rs.getString("id"), rs.getString("content_type"), rs.getString("original_name")));
        }, id);
    }

    private StoredFile fileRecord(String id) {
        return jdbcTemplate.query("SELECT id, object_key, original_name, content_type, size_bytes FROM files WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "文件未找到");
            return new StoredFile(rs.getString("id"), rs.getString("object_key"), rs.getString("original_name"),
                    rs.getString("content_type"), rs.getLong("size_bytes"));
        }, id);
    }

    private FileUploadSessionRecord uploadSessionRecord(String id) {
        return jdbcTemplate.query("SELECT id, uploader_id, original_name, content_type, total_size, total_chunks, object_key, status FROM file_upload_sessions WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "上传会话未找到");
            return new FileUploadSessionRecord(rs.getString("id"), rs.getString("uploader_id"), rs.getString("original_name"), rs.getString("content_type"),
                    rs.getLong("total_size"), rs.getInt("total_chunks"), rs.getString("object_key"), rs.getString("status"));
        }, id);
    }

    private FileUploadSessionDto uploadSessionById(String id) {
        val record = uploadSessionRecord(id);
        val uploadedChunks = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_upload_chunks WHERE session_id = ?", Integer.class, id);
        val uploadedBytes = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(size_bytes), 0) FROM file_upload_chunks WHERE session_id = ?", Long.class, id);
        return new FileUploadSessionDto(record.id(), record.uploaderId(), record.originalName(), record.contentType(), record.totalSize(), record.totalChunks(),
                uploadedChunks == null ? 0 : uploadedChunks, uploadedBytes == null ? 0 : uploadedBytes, record.status());
    }

    private FileTransferDto transferById(String id) {
        return jdbcTemplate.query("SELECT id, file_id, user_id, direction, progress, status, updated_at FROM file_transfer_logs WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "传输记录未找到");
            return new FileTransferDto(rs.getString("id"), rs.getString("file_id"), rs.getString("user_id"), rs.getString("direction"),
                    rs.getInt("progress"), rs.getString("status"), toInstant(rs.getTimestamp("updated_at")));
        }, id);
    }

    private void ensureConversation(String conversationId, String type, String targetId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversations WHERE id = ?", Integer.class, conversationId);
        if (count != null && count > 0) return;
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id) VALUES (?, ?, ?)", conversationId, valueOr(type, "single"), valueOr(targetId, conversationId));
    }

    private ConversationSettingsDto conversationSettings(String conversationId, String userId) {
        return jdbcTemplate.query("SELECT muted, pinned, screenshot_notice, recall_notice, read_after_burn, strong_reminder, display_member_nicknames, saved_to_contacts " +
                "FROM conversation_members WHERE conversation_id = ? AND user_id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "会话设置未找到");
            return new ConversationSettingsDto(conversationId, rs.getBoolean("muted"), rs.getBoolean("pinned"),
                    rs.getBoolean("screenshot_notice"), rs.getBoolean("recall_notice"), rs.getBoolean("read_after_burn"),
                    rs.getBoolean("strong_reminder"), rs.getBoolean("display_member_nicknames"), rs.getBoolean("saved_to_contacts"));
        }, conversationId, userId);
    }

    private void ensureNotificationSettings(String userId) {
        if (!exists("SELECT COUNT(*) FROM user_notification_settings WHERE user_id = ?", userId)) {
            jdbcTemplate.update("INSERT INTO user_notification_settings(user_id) VALUES (?)", userId);
        }
    }

    private NotificationSettingsDto notificationSettingsByUser(String userId) {
        return jdbcTemplate.query("SELECT user_id, new_message, calls, detail, sound, vibration, screenshot_notice, recall_notice, mention_alert, dnd_enabled, dnd_start, dnd_end, updated_at " +
                "FROM user_notification_settings WHERE user_id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "通知设置未找到");
            return new NotificationSettingsDto(rs.getString("user_id"), rs.getBoolean("new_message"), rs.getBoolean("calls"),
                    rs.getBoolean("detail"), rs.getBoolean("sound"), rs.getBoolean("vibration"),
                    rs.getBoolean("screenshot_notice"), rs.getBoolean("recall_notice"), rs.getBoolean("mention_alert"),
                    rs.getBoolean("dnd_enabled"), rs.getString("dnd_start"), rs.getString("dnd_end"), toInstant(rs.getTimestamp("updated_at")));
        }, userId);
    }

    private PushDeviceTokenDto pushDeviceTokenById(String id) {
        return jdbcTemplate.query("SELECT id, user_id, platform, provider, token, enabled, updated_at FROM push_device_tokens WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "推送设备令牌未找到");
            return new PushDeviceTokenDto(rs.getString("id"), rs.getString("user_id"), rs.getString("platform"), rs.getString("provider"),
                    maskToken(rs.getString("token")), rs.getBoolean("enabled"), toInstant(rs.getTimestamp("updated_at")));
        }, id);
    }

    private String config(String key, String fallback) {
        val values = jdbcTemplate.queryForList("SELECT config_value FROM system_configs WHERE config_key = ? LIMIT 1", String.class, key);
        return values.isEmpty() || values.get(0) == null ? fallback : values.get(0);
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) return "****";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private void enforceConversationSendPolicy(String conversationId, String senderId) {
        val row = jdbcTemplate.queryForMap("SELECT type, target_id FROM conversations WHERE id = ?", conversationId);
        val type = mapString(row, "type");
        val targetId = mapString(row, "target_id");
        if ("single".equals(type) && hasText(targetId) && !senderId.equals(targetId)) {
            if (exists("SELECT COUNT(*) FROM blacklists WHERE user_id = ? AND blocked_user_id = ?", senderId, targetId) ||
                    exists("SELECT COUNT(*) FROM blacklists WHERE user_id = ? AND blocked_user_id = ?", targetId, senderId)) {
                throw new ResponseStatusException(FORBIDDEN, "会话已被黑名单拦截");
            }
        }
        if ("group".equals(type) && exists("SELECT COUNT(*) FROM chat_groups WHERE id = ?", conversationId)) {
            if (!exists("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?", conversationId, senderId)) {
                throw new ResponseStatusException(FORBIDDEN, "需要是群成员");
            }
            if (exists("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ? AND muted = TRUE", conversationId, senderId)) {
                throw new ResponseStatusException(FORBIDDEN, "群成员已被禁言");
            }
        }
    }

    private void ensureMember(String conversationId, String userId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversation_members WHERE conversation_id = ? AND user_id = ?", Integer.class, conversationId, userId);
        if (count != null && count > 0) return;
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, userId);
    }

    private void ensureFileExists(String fileId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM files WHERE id = ?", Integer.class, fileId);
        if (count == null || count == 0) throw new ResponseStatusException(BAD_REQUEST, "文件未找到");
    }

    private void requireGroupOwner(String groupId, String userId) {
        if (!exists("SELECT COUNT(*) FROM chat_groups WHERE id = ? AND owner_id = ?", groupId, userId)) {
            throw new ResponseStatusException(FORBIDDEN, "需要群主权限");
        }
    }

    private void validateFilePolicy(String fileName, String contentType, long sizeBytes) {
        val maxMb = Long.parseLong(policyValue("max_file_size_mb", "200"));
        if (sizeBytes > maxMb * 1024L * 1024L) {
            throw new ResponseStatusException(BAD_REQUEST, "文件超出大小限制");
        }
        val ext = extension(fileName);
        val allowed = new HashSet<String>(Arrays.asList(policyValue("allowed_file_types", "").toLowerCase().split(",")));
        if (!allowed.isEmpty() && !allowed.contains(ext)) {
            throw new ResponseStatusException(BAD_REQUEST, "文件类型被策略拦截");
        }
    }

    private void enforceTransferRate(String userId, String direction, String countPolicyKey, String mbPolicyKey, long nextBytes) {
        val limit = Integer.parseInt(policyValue(countPolicyKey, "1000000"));
        val since = Timestamp.from(Instant.now().minusSeconds(60));
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_transfer_logs WHERE user_id = ? AND direction = ? AND created_at >= ?",
                Integer.class, userId, direction, since);
        if (count != null && count >= limit) {
            throw new ResponseStatusException(BAD_REQUEST, "文件传输频率超限");
        }
        val byteLimit = Long.parseLong(policyValue(mbPolicyKey, "1000000")) * 1024L * 1024L;
        val usedBytes = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(size_bytes), 0) FROM file_transfer_logs WHERE user_id = ? AND direction = ? AND created_at >= ?",
                Long.class, userId, direction, since);
        if ((usedBytes == null ? 0L : usedBytes) + Math.max(0L, nextBytes) > byteLimit) {
            throw new ResponseStatusException(BAD_REQUEST, "文件传输带宽超限");
        }
    }

    private String policyValue(String key, String fallback) {
        return jdbcTemplate.query("SELECT policy_value FROM resource_policies WHERE policy_key = ?", rs -> rs.next() ? rs.getString("policy_value") : fallback, key);
    }

    private Path storagePath(String objectKey) {
        val root = Paths.get(properties.getStorage().getLocalRoot(), properties.getStorage().getBucket()).toAbsolutePath().normalize();
        val target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) throw new ResponseStatusException(BAD_REQUEST, "无效的对象存储路径");
        return target;
    }

    private String safeFileName(String fileName) {
        val name = valueOr(fileName, "file.bin").replace("\\", "_").replace("/", "_");
        return name.trim().isEmpty() ? "file.bin" : name;
    }

    private String extension(String fileName) {
        val name = safeFileName(fileName).toLowerCase();
        val dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "bin";
    }

    private boolean isPreviewable(String contentType, String fileName) {
        val type = valueOr(contentType, "").toLowerCase();
        if (type.startsWith("image/") || type.startsWith("video/") || type.equals("application/pdf") || type.startsWith("text/")) return true;
        val ext = extension(fileName);
        return Arrays.asList("pdf", "png", "jpg", "jpeg", "gif", "webp", "mp4", "txt", "md", "csv").contains(ext);
    }

    private boolean isOcrCandidate(String contentType, String fileName) {
        val type = valueOr(contentType, "").toLowerCase();
        if (type.startsWith("image/") || type.equals("application/pdf")) return true;
        val ext = extension(fileName);
        return Arrays.asList("pdf", "png", "jpg", "jpeg", "gif", "webp", "bmp").contains(ext);
    }

    private String previewUrl(String id, String contentType, String fileName) {
        return isPreviewable(contentType, fileName) ? "/api/files/" + id + "/preview" : null;
    }

    private MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(valueOr(value, "application/octet-stream"));
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private void insertTransferLog(String fileId, String userId, String direction, int progress, String status, long sizeBytes) {
        jdbcTemplate.update("INSERT INTO file_transfer_logs(id, file_id, user_id, direction, progress, status, size_bytes) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "ft_" + UUID.randomUUID(), fileId, userId, direction, progress, status, Math.max(0L, sizeBytes));
    }

    private void ensureMessageEditable(MessageDto message) {
        ensureMessageWithinWindow(message, "message edit window expired");
        ensureMessageActive(message);
        if (!"text".equals(valueOr(message.type(), "text"))) {
            throw new ResponseStatusException(BAD_REQUEST, "仅文本消息可以编辑");
        }
    }

    private void ensureMessageActive(MessageDto message) {
        val status = valueOr(message.status(), "sent");
        if ("recalled".equals(status) || "destroyed".equals(status)) {
            throw new ResponseStatusException(BAD_REQUEST, "消息已失效");
        }
    }

    private void ensureMessageWithinWindow(MessageDto message, String reason) {
        if (message.createdAt() == null || Instant.parse(message.createdAt()).isBefore(Instant.now().minusSeconds(180))) {
            throw new ResponseStatusException(BAD_REQUEST, reason);
        }
    }

    private List<String> uniqueNonBlank(List<String> values) {
        val result = new ArrayList<String>();
        if (values == null) return result;
        val seen = new LinkedHashSet<String>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) seen.add(value.trim());
        }
        result.addAll(seen);
        return result;
    }

    private String combinedForwardContent(List<MessageDto> originals) {
        val lines = new ArrayList<String>();
        for (MessageDto original : originals) {
            lines.add(original.senderId() + ": " + valueOr(original.content(), "[" + original.type() + "]"));
        }
        return String.join("\n", lines);
    }

    private String firstBlockedWord(String content) {
        if (content == null || content.trim().isEmpty()) return null;
        val words = jdbcTemplate.queryForList("SELECT word FROM sensitive_words WHERE enabled = TRUE AND action = 'block'", String.class);
        for (String word : words) {
            if (content.contains(word)) return word;
        }
        return null;
    }

    private void insertRisk(String type, String userId, String conversationId, String messageId, String detail) {
        jdbcTemplate.update("INSERT INTO risk_events(id, event_type, user_id, conversation_id, message_id, detail) VALUES (?, ?, ?, ?, ?, ?)",
                "risk_" + UUID.randomUUID(), type, userId, conversationId, messageId, detail);
    }

    private boolean exists(String sql, Object... params) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private String mapString(Map<String, Object> row, String key) {
        val value = row.get(key);
        if (value != null) return String.valueOf(value);
        val upper = row.get(key.toUpperCase());
        return upper == null ? null : String.valueOf(upper);
    }

    private int clampProgress(Integer progress) {
        if (progress == null) return 0;
        return Math.max(0, Math.min(100, progress));
    }

    private String toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class MessageCreateRequest { private String senderId; private String type; private String content; private String fileId; private String clientSeq; private String conversationType; private String targetId; private boolean expireAfterRead; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class MessageEditRequest { private String content; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class RecallRequest { private String reason; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ReactionRequest { private String reaction; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FileCreateRequest { private String uploaderId; private String objectKey; private String originalName; private String contentType; private long sizeBytes; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FileUploadSessionRequest { private String uploaderId; private String originalName; private String contentType; private long totalSize; private int totalChunks; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FileUploadSessionDto { private String id; private String uploaderId; private String originalName; private String contentType; private long totalSize; private int totalChunks; private int uploadedChunks; private long uploadedBytes; private String status; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FilePreviewAdapterDto { private String fileId; private String providerMode; private boolean enabled; private String message; private String result; private String previewUrl; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FileTransferRequest { private String direction; private Integer progress; private String status; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class MessageDto { private String id; private String conversationId; private String senderId; private String type; private String content; private String fileId; private String status; private String clientSeq; private String createdAt; private String editedAt; private String recalledAt; private boolean expireAfterRead; private String destroyedAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ReceiptDto { private String messageId; private String userId; private String readAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ReadMemberDto { private String userId; private String userName; private String readAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class MessageReadStatusDto { private String messageId; private String conversationId; private List<ReadMemberDto> read; private List<ReadMemberDto> unread; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ReactionDto { private String messageId; private String userId; private String reaction; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FavoriteDto { private String userId; private String messageId; private boolean favorited; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ScreenshotDto { private String id; private String conversationId; private String userId; private String createdAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FileDto { private String id; private String uploaderId; private String originalName; private String contentType; private long sizeBytes; private String status; private String createdAt; private String downloadUrl; private String previewUrl; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FileTransferDto { private String id; private String fileId; private String userId; private String direction; private int progress; private String status; private String updatedAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ConversationDto { private String id; private String type; private String targetId; private boolean muted; private boolean pinned; private int unreadCount; private String lastContent; private String lastSender; private String lastTime; private String lastType; private String updatedAt; private boolean screenshotNotice; private boolean recallNotice; private boolean readAfterBurn; private boolean strongReminder; private boolean displayMemberNicknames; private boolean savedToContacts; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ConversationSettingsDto { private String conversationId; private boolean muted; private boolean pinned; private boolean screenshotNotice; private boolean recallNotice; private boolean readAfterBurn; private boolean strongReminder; private boolean displayMemberNicknames; private boolean savedToContacts; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ConversationSettingsRequest { private Boolean muted; private Boolean pinned; private Boolean screenshotNotice; private Boolean recallNotice; private Boolean readAfterBurn; private Boolean strongReminder; private Boolean displayMemberNicknames; private Boolean savedToContacts; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class NotificationSettingsDto { private String userId; private boolean newMessage; private boolean calls; private boolean detail; private boolean sound; private boolean vibration; private boolean screenshotNotice; private boolean recallNotice; private boolean mentionAlert; private boolean dndEnabled; private String dndStart; private String dndEnd; private String updatedAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class NotificationSettingsRequest { private Boolean newMessage; private Boolean calls; private Boolean detail; private Boolean sound; private Boolean vibration; private Boolean screenshotNotice; private Boolean recallNotice; private Boolean mentionAlert; private Boolean dndEnabled; private String dndStart; private String dndEnd; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class PushProviderDto { private String name; private boolean enabled; private String mode; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class PushProvidersDto { private PushProviderDto apns; private PushProviderDto fcm; private PushProviderDto vendor; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class PushDeviceTokenRequest { private String platform; private String provider; private String token; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class PushDeviceTokenDto { private String id; private String userId; private String platform; private String provider; private String token; private boolean enabled; private String updatedAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class WorkspaceAppDto { private String id; private String name; private String icon; private String url; private String visibleDepartmentId; private int sortOrder; private boolean enabled; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ForwardRequest { private String targetConversationId; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ForwardBatchRequest { private List<String> messageIds; private List<String> targetConversationIds; private String mode; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SearchResultDto { private List<SearchUserItem> contacts; private List<SearchGroupItem> groups; private List<SearchMessageItem> messages; private List<SearchFileItem> files; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SearchUserItem { private String id; private String name; private String phone; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SearchGroupItem { private String id; private String name; private String notice; private String jumpUrl; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SearchMessageItem { private String id; private String conversationId; private String senderId; private String type; private String content; private String createdAt; private String jumpUrl; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SearchFileItem { private String id; private String name; private String contentType; private long sizeBytes; private String jumpUrl; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SearchRecommendationDto { private List<SearchUserItem> contacts; private List<SearchGroupItem> groups; private List<SearchMessageItem> messages; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupMemberDto { private String userId; private String userName; private String role; private String alias; private boolean muted; private String joinedAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupMemberAddRequest { private List<String> userIds; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class MuteRequest { private boolean muted; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupUpdateRequest { private String name; private String notice; private String avatarUrl; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class TransferOwnerRequest { private String newOwnerId; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class StoredFile { private String id; private String objectKey; private String originalName; private String contentType; private long sizeBytes; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FileUploadSessionRecord { private String id; private String uploaderId; private String originalName; private String contentType; private long totalSize; private int totalChunks; private String objectKey; private String status; }
}
