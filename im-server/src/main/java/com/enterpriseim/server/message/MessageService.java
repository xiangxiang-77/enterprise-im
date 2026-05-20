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
        val messageId = "m_" + UUID.randomUUID();

        userService.ensureUser(from, from, null);
        userService.ensureUser(to, to, null);
        ensureConversation(conversationId, "single", to);
        ensureConversationMember(conversationId, from);
        ensureConversationMember(conversationId, to);

        jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, status, client_seq)\n" +
                "VALUES (?, ?, ?, 'text', ?, 'sent', ?)\n", messageId, conversationId, from, content, message.requestId());

        return new PersistedMessage(messageId, conversationId, from, to, content);
    }

    public List<MessageItem> listMessages(String conversationId, int limit) {
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("SELECT id, conversation_id, sender_id, type, content, status, client_seq, created_at\n" +
                "FROM messages\n" +
                "WHERE conversation_id = ?\n" +
                "ORDER BY created_at ASC, id ASC\n" +
                "LIMIT ?\n", (rs, rowNum) -> new MessageItem(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("sender_id"),
                rs.getString("type"),
                rs.getString("content"),
                rs.getString("status"),
                rs.getString("client_seq"),
                toInstant(rs.getTimestamp("created_at"))
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
    private String status;
    private String clientSeq;
    private Instant createdAt;
}

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
