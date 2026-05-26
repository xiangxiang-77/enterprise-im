package com.enterpriseim.server.ws;

import lombok.val;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    public void bind(String userId, WebSocketSession session) {
        userSessions.put(userId, session);
        sessionUsers.put(session.getId(), userId);
    }

    public Optional<WebSocketSession> findSession(String userId) {
        return Optional.ofNullable(userSessions.get(userId)).filter(WebSocketSession::isOpen);
    }

    public void remove(WebSocketSession session) {
        val userId = sessionUsers.remove(session.getId());
        if (userId != null) {
            userSessions.remove(userId, session);
        }
    }

    public int onlineCount() {
        return userSessions.size();
    }

    public boolean isOnline(String userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }

    public boolean disconnect(String userId) {
        WebSocketSession session = userSessions.remove(userId);
        if (session == null) return false;
        sessionUsers.remove(session.getId());
        try {
            session.close();
        } catch (IOException ignored) {
            // Session is already removed from the authoritative registry.
        }
        return true;
    }
}

