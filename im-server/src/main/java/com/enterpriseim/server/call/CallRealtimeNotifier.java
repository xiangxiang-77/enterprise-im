package com.enterpriseim.server.call;

import lombok.val;

import com.enterpriseim.server.tcp.OnlineSessionRegistry;
import com.enterpriseim.server.tcp.TcpMessage;
import com.enterpriseim.server.ws.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

@Component
public class CallRealtimeNotifier {
    private final ObjectMapper objectMapper;
    private final OnlineSessionRegistry tcpSessions;
    private final WebSocketSessionRegistry webSocketSessions;

    public CallRealtimeNotifier(ObjectMapper objectMapper, OnlineSessionRegistry tcpSessions, WebSocketSessionRegistry webSocketSessions) {
        this.objectMapper = objectMapper;
        this.tcpSessions = tcpSessions;
        this.webSocketSessions = webSocketSessions;
    }

    public void notifyCallStarted(CallService.CallRecord call) {
        notifyParticipant(call.calleeId(), "CALL_INVITE", call);
        notifyParticipant(call.callerId(), "CALL_UPDATE", call);
    }

    public void notifyCallUpdated(CallService.CallRecord call) {
        notifyParticipant(call.callerId(), "CALL_UPDATE", call);
        notifyParticipant(call.calleeId(), "CALL_UPDATE", call);
    }

    private void notifyParticipant(String userId, String type, CallService.CallRecord call) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        val message = new TcpMessage(
                "1",
                type,
                call.id(),
                "server",
                userId,
                call.conversationId(),
                System.currentTimeMillis(),
                payload(call)
        );
        notifyTcp(userId, message);
        notifyWebSocket(userId, message);
    }

    private void notifyTcp(String userId, TcpMessage message) {
        tcpSessions.findChannel(userId).ifPresent(channel -> channel.writeAndFlush(toLine(message)));
    }

    private void notifyWebSocket(String userId, TcpMessage message) {
        webSocketSessions.findSession(userId).ifPresent(session -> {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to notify websocket call event", e);
            }
        });
    }

    private ObjectNode payload(CallService.CallRecord call) {
        return objectMapper.valueToTree(call);
    }

    private String toLine(TcpMessage message) {
        try {
            return objectMapper.writeValueAsString(message) + "\n";
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode call event", e);
        }
    }
}
