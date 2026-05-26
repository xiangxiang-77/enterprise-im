package com.enterpriseim.server.feature;

import com.enterpriseim.server.config.ImProperties;
import com.enterpriseim.server.push.ApnsPushProvider;
import com.enterpriseim.server.push.FcmPushProvider;
import com.enterpriseim.server.push.PushProvider;
import com.enterpriseim.server.tcp.OnlineSessionRegistry;
import com.enterpriseim.server.tcp.TcpMessage;
import com.enterpriseim.server.ws.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.val;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.time.LocalTime;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class NotificationService {
    private static final Logger LOG = Logger.getLogger(NotificationService.class.getName());

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OnlineSessionRegistry tcpSessions;
    private final WebSocketSessionRegistry wsSessions;
    private final ImProperties properties;
    private final PushProvider pushProvider;

    public NotificationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, OnlineSessionRegistry tcpSessions, WebSocketSessionRegistry wsSessions, ImProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tcpSessions = tcpSessions;
        this.wsSessions = wsSessions;
        this.properties = properties;
        this.pushProvider = createPushProvider();
    }

    private PushProvider createPushProvider() {
        String provider = properties.getPush().getProvider();
        if ("fcm".equalsIgnoreCase(provider)) {
            LOG.info("Initializing FCM push provider");
            return new FcmPushProvider(properties, objectMapper);
        } else if ("apns".equalsIgnoreCase(provider)) {
            LOG.info("Initializing APNs push provider");
            return new ApnsPushProvider(properties);
        } else {
            LOG.info("Push provider set to mock (no-op); real pushes will not be sent");
            return null;
        }
    }

    public void notifyConversation(String conversationId, String actorId, String eventType, String messageId, String content) {
        val recipients = jdbcTemplate.query("SELECT cm.user_id, cm.muted, cm.screenshot_notice, cm.recall_notice, cm.strong_reminder, " +
                        "COALESCE(ns.new_message, TRUE) AS new_message, COALESCE(ns.screenshot_notice, TRUE) AS global_screenshot_notice, " +
                        "COALESCE(ns.recall_notice, TRUE) AS global_recall_notice, COALESCE(ns.mention_alert, TRUE) AS mention_alert, " +
                        "COALESCE(ns.dnd_enabled, FALSE) AS dnd_enabled, COALESCE(ns.dnd_start, '22:00') AS dnd_start, COALESCE(ns.dnd_end, '08:00') AS dnd_end " +
                        "FROM conversation_members cm LEFT JOIN user_notification_settings ns ON ns.user_id = cm.user_id " +
                        "WHERE cm.conversation_id = ? AND cm.user_id <> ?",
                (rs, rowNum) -> new Recipient(
                        rs.getString("user_id"),
                        rs.getBoolean("muted"),
                        rs.getBoolean("screenshot_notice"),
                        rs.getBoolean("recall_notice"),
                        rs.getBoolean("strong_reminder"),
                        rs.getBoolean("new_message"),
                        rs.getBoolean("global_screenshot_notice"),
                        rs.getBoolean("global_recall_notice"),
                        rs.getBoolean("mention_alert"),
                        rs.getBoolean("dnd_enabled"),
                        rs.getString("dnd_start"),
                        rs.getString("dnd_end")
                ), conversationId, actorId);
        for (Recipient recipient : recipients) {
            val decision = decision(recipient, eventType, content);
            insertEvent(recipient.userId, conversationId, actorId, messageId, eventType, decision.status, decision.reason);
            if ("delivered".equals(decision.status)) {
                deliver(recipient.userId, conversationId, actorId, eventType, messageId, content);
            }
        }
    }

    private Decision decision(Recipient recipient, String eventType, String content) {
        val mention = isMentioned(recipient.userId, content);
        if ("screenshot".equals(eventType) && (!recipient.screenshotNotice || !recipient.globalScreenshotNotice)) {
            return new Decision("suppressed", "screenshot_notice_disabled");
        }
        if ("recall".equals(eventType) && (!recipient.recallNotice || !recipient.globalRecallNotice)) {
            return new Decision("suppressed", "recall_notice_disabled");
        }
        if (!recipient.newMessage && !"screenshot".equals(eventType) && !"recall".equals(eventType)) {
            return new Decision("suppressed", "new_message_disabled");
        }
        if (recipient.muted && !(mention && recipient.mentionAlert)) {
            return new Decision("suppressed", "conversation_muted");
        }
        if (recipient.dndEnabled && inDnd(recipient.dndStart, recipient.dndEnd) && !(mention && recipient.mentionAlert)) {
            return new Decision("suppressed", "dnd");
        }
        return new Decision("delivered", mention ? "mention_override" : "ok");
    }

    private boolean isMentioned(String userId, String content) {
        if (content == null) return false;
        return content.contains("@" + userId) || content.contains("@all");
    }

    private boolean inDnd(String start, String end) {
        try {
            val now = LocalTime.now();
            val s = LocalTime.parse(start);
            val e = LocalTime.parse(end);
            if (s.equals(e)) return true;
            if (s.isBefore(e)) return !now.isBefore(s) && now.isBefore(e);
            return !now.isBefore(s) || now.isBefore(e);
        } catch (Exception ex) {
            return false;
        }
    }

    private void insertEvent(String userId, String conversationId, String actorId, String messageId, String eventType, String status, String reason) {
        jdbcTemplate.update("INSERT INTO notification_events(id, user_id, conversation_id, actor_id, message_id, event_type, status, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "ntf_" + UUID.randomUUID(), userId, conversationId, actorId, messageId, eventType, status, reason);
    }

    private void deliver(String userId, String conversationId, String actorId, String eventType, String messageId, String content) {
        val payload = JsonNodeFactory.instance.objectNode();
        payload.put("eventType", eventType);
        payload.put("messageId", messageId);
        payload.put("content", content == null ? "" : content);
        val message = new TcpMessage("1", "NOTIFICATION", "ntf_" + UUID.randomUUID(), actorId, userId, conversationId, System.currentTimeMillis(), payload);
        val tcp = tcpSessions.findChannel(userId);
        val ws = wsSessions.findSession(userId);
        tcp.ifPresent(channel -> channel.writeAndFlush(toLine(message)));
        ws.ifPresent(session -> {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to notify websocket event", ex);
            }
        });
        if (!tcp.isPresent() && !ws.isPresent()) {
            enqueueExternalPush(userId, eventType, messageId);
        }
    }

    private void enqueueExternalPush(String userId, String eventType, String messageId) {
        val rows = jdbcTemplate.query("SELECT id, provider, token FROM push_device_tokens WHERE user_id = ? AND enabled = TRUE",
                (rs, rowNum) -> new PushTarget(rs.getString("id"), rs.getString("provider"), rs.getString("token")), userId);
        for (PushTarget row : rows) {
            if (pushProvider != null && pushProvider.name().equalsIgnoreCase(row.provider)) {
                boolean success = pushProvider.sendPush(row.token, "新消息", "您有一条新消息", Collections.<String, String>emptyMap());
                String status = success ? "delivered" : "failed";
                String reason = success ? "provider_sent" : "provider_send_error";
                jdbcTemplate.update("INSERT INTO push_deliveries(id, user_id, device_token_id, provider, event_type, message_id, status, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        "push_" + UUID.randomUUID(), userId, row.id, row.provider, eventType, messageId, status, reason);
            } else {
                val mode = config("push." + row.provider + ".provider", "disabled");
                val status = "disabled".equals(mode) ? "skipped" : "queued";
                val reason = "disabled".equals(mode) ? "provider_disabled" : "provider_boundary_no_adapter";
                jdbcTemplate.update("INSERT INTO push_deliveries(id, user_id, device_token_id, provider, event_type, message_id, status, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        "push_" + UUID.randomUUID(), userId, row.id, row.provider, eventType, messageId, status, reason);
            }
        }
    }

    private String config(String key, String fallback) {
        val values = jdbcTemplate.queryForList("SELECT config_value FROM system_configs WHERE config_key = ? LIMIT 1", String.class, key);
        return values.isEmpty() || values.get(0) == null ? fallback : values.get(0);
    }

    private String toLine(TcpMessage message) {
        try {
            return objectMapper.writeValueAsString(message) + "\n";
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encode notification", ex);
        }
    }

    private static class Recipient {
        private final String userId;
        private final boolean muted;
        private final boolean screenshotNotice;
        private final boolean recallNotice;
        private final boolean strongReminder;
        private final boolean newMessage;
        private final boolean globalScreenshotNotice;
        private final boolean globalRecallNotice;
        private final boolean mentionAlert;
        private final boolean dndEnabled;
        private final String dndStart;
        private final String dndEnd;

        private Recipient(String userId, boolean muted, boolean screenshotNotice, boolean recallNotice, boolean strongReminder,
                          boolean newMessage, boolean globalScreenshotNotice, boolean globalRecallNotice,
                          boolean mentionAlert, boolean dndEnabled, String dndStart, String dndEnd) {
            this.userId = userId;
            this.muted = muted;
            this.screenshotNotice = screenshotNotice;
            this.recallNotice = recallNotice;
            this.strongReminder = strongReminder;
            this.newMessage = newMessage;
            this.globalScreenshotNotice = globalScreenshotNotice;
            this.globalRecallNotice = globalRecallNotice;
            this.mentionAlert = mentionAlert;
            this.dndEnabled = dndEnabled;
            this.dndStart = dndStart;
            this.dndEnd = dndEnd;
        }
    }

    private static class Decision {
        private final String status;
        private final String reason;

        private Decision(String status, String reason) {
            this.status = status;
            this.reason = reason;
        }
    }

    private static class PushTarget {
        private final String id;
        private final String provider;
        private final String token;

        private PushTarget(String id, String provider, String token) {
            this.id = id;
            this.provider = provider;
            this.token = token;
        }
    }
}
