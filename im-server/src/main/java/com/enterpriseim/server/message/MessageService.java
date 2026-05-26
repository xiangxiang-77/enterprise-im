package com.enterpriseim.server.message;

import lombok.val;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.enterpriseim.server.tcp.TcpMessage;
import com.enterpriseim.server.user.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {
    private final JdbcTemplate jdbcTemplate;
    private final UserService userService;

    public MessageService(JdbcTemplate jdbcTemplate, UserService userService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userService = userService;
    }

    @Transactional
    public PersistedMessage persistText(TcpMessage message) {
        val from = required(message.from(), "from");
        val to = required(message.to(), "to");
        val conversationId = required(message.conversationId(), "conversationId");
        val content = message.payload() == null ? "" : message.payload().path("content").asText("");
        val type = message.payload() == null ? "text" : valueOr(message.payload().path("messageType").asText("text"), "text");
        val fileId = message.payload() == null ? null : emptyToNull(message.payload().path("fileId").asText(null));
        val messageId = "m_" + UUID.randomUUID();

        userService.ensureUser(from, from, null);
        userService.ensureUser(to, to, null);
        ensureConversation(conversationId, "single", to);
        ensureConversationMember(conversationId, from);
        ensureConversationMember(conversationId, to);

        jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, file_id, status, client_seq)\n" +
                "VALUES (?, ?, ?, ?, ?, ?, 'sent', ?)\n", messageId, conversationId, from, type, content, fileId, message.requestId());

        return new PersistedMessage(messageId, conversationId, from, to, content);
    }

    public List<MessageItem> listMessages(String conversationId, int limit) {
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("SELECT id, conversation_id, sender_id, type, content, file_id, status, client_seq, created_at, edited_at, recalled_at, expire_after_read, destroyed_at\n" +
                "FROM messages\n" +
                "WHERE conversation_id = ?\n" +
                "ORDER BY created_at ASC, id ASC\n" +
                "LIMIT ?\n", (rs, rowNum) -> new MessageItem(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("sender_id"),
                rs.getString("type"),
                rs.getString("content"),
                rs.getString("file_id"),
                rs.getString("status"),
                rs.getString("client_seq"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("edited_at")),
                toInstant(rs.getTimestamp("recalled_at")),
                rs.getBoolean("expire_after_read"),
                toInstant(rs.getTimestamp("destroyed_at"))
        ), conversationId, boundedLimit);
    }

    private void ensureConversation(String conversationId, String type, String targetId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversations WHERE id = ?", Integer.class, conversationId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id)\n" +
                "VALUES (?, ?, ?)\n", conversationId, type, targetId);
    }

    private void ensureConversationMember(String conversationId, String userId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversation_members\n" +
                "WHERE conversation_id = ? AND user_id = ?\n", Integer.class, conversationId, userId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id)\n" +
                "VALUES (?, ?)\n", conversationId, userId);
    }

    private String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class PersistedMessage {
    private String messageId;
    private String conversationId;
    private String from;
    private String to;
    private String content;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class MessageItem {
    private String id;
    private String conversationId;
    private String senderId;
    private String type;
    private String content;
    private String fileId;
    private String status;
    private String clientSeq;
    private Instant createdAt;
    private Instant editedAt;
    private Instant recalledAt;
    private boolean expireAfterRead;
    private Instant destroyedAt;
}

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
