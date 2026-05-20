package com.enterpriseim.server.ws;

import lombok.val;

import com.enterpriseim.server.config.ImProperties;
import com.enterpriseim.server.message.MessageService;
import com.enterpriseim.server.tcp.TcpMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ImWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry sessions;
    private final ImProperties properties;
    private final MessageService messageService;

    public ImWebSocketHandler(ObjectMapper objectMapper, WebSocketSessionRegistry sessions, ImProperties properties, MessageService messageService) {
        this.objectMapper = objectMapper;
        this.sessions = sessions;
        this.properties = properties;
        this.messageService = messageService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        val message = objectMapper.readValue(textMessage.getPayload(), TcpMessage.class);
        switch (message.type()) {
            case "AUTH":
                handleAuth(session, message);
                break;
            case "PING":
                write(session, response(message, "PONG", payload("onlineCount", sessions.onlineCount())));
                break;
            case "TEXT":
                handleText(session, message);
                break;
            case "WEBRTC_SIGNAL":
                handleWebRtcSignal(session, message);
                break;
            case "ACK":
                write(session, response(message, "ACK_OK", payload("ack", true)));
                break;
            default:
                write(session, response(message, "ERROR", payload("message", "unsupported websocket message type")));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private void handleAuth(WebSocketSession session, TcpMessage message) throws Exception {
        val token = message.payload() == null ? "" : message.payload().path("token").asText("");
        if (!token.startsWith(properties.getAuth().getDemoTokenPrefix())) {
            write(session, response(message, "AUTH_FAILED", payload("message", "invalid token")));
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
            return;
        }

        val userId = token.substring(properties.getAuth().getDemoTokenPrefix().length());
        sessions.bind(userId, session);
        write(session, response(message, "AUTH_OK", payload("userId", userId)));
    }

    private void handleText(WebSocketSession session, TcpMessage message) throws Exception {
        val persisted = messageService.persistText(message);
        val ackPayload = payload("serverTime", System.currentTimeMillis());
        ackPayload.put("messageId", persisted.messageId());
        write(session, response(message, "ACK", ackPayload));
        if (message.to() == null || message.to().trim().isEmpty()) {
            return;
        }

        val delivery = new TcpMessage(
                message.version(),
                "TEXT_DELIVER",
                message.requestId(),
                message.from(),
                message.to(),
                message.conversationId(),
                System.currentTimeMillis(),
                message.payload()
        );
        sessions.findSession(message.to()).ifPresent(target -> {
            try {
                write(target, delivery);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deliver websocket message", e);
            }
        });
    }

    private void handleWebRtcSignal(WebSocketSession session, TcpMessage message) throws Exception {
        write(session, response(message, "ACK", payload("serverTime", System.currentTimeMillis())));
        if (message.to() == null || message.to().trim().isEmpty()) {
            return;
        }

        val delivery = new TcpMessage(
                message.version(),
                "WEBRTC_SIGNAL",
                message.requestId(),
                message.from(),
                message.to(),
                message.conversationId(),
                System.currentTimeMillis(),
                message.payload()
        );
        sessions.findSession(message.to()).ifPresent(target -> {
            try {
                write(target, delivery);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deliver websocket WebRTC signal", e);
            }
        });
    }

    private TcpMessage response(TcpMessage source, String type, ObjectNode payload) {
        return new TcpMessage(
                source.version() == null ? "1" : source.version(),
                type,
                source.requestId(),
                "server",
                source.from(),
                source.conversationId(),
                System.currentTimeMillis(),
                payload
        );
    }

    private ObjectNode payload(String key, Object value) {
        val node = JsonNodeFactory.instance.objectNode();
        if (value instanceof Boolean) {
            node.put(key, (Boolean) value);
        } else if (value instanceof Integer) {
            node.put(key, (Integer) value);
        } else if (value instanceof Long) {
            node.put(key, (Long) value);
        } else {
            node.put(key, String.valueOf(value));
        }
        return node;
    }

    private void write(WebSocketSession session, TcpMessage message) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }
}
