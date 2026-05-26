package com.enterpriseim.server.admin;

import com.enterpriseim.server.tcp.OnlineSessionRegistry;
import com.enterpriseim.server.ws.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class AdminWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final OnlineSessionRegistry tcpSessions;
    private final WebSocketSessionRegistry webSocketSessions;
    private final AdminAuthService authService;

    private final Map<String, ScheduledFuture<?>> sessionTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public AdminWebSocketHandler(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                                 OnlineSessionRegistry tcpSessions, WebSocketSessionRegistry webSocketSessions,
                                 AdminAuthService authService) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.tcpSessions = tcpSessions;
        this.webSocketSessions = webSocketSessions;
        this.authService = authService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null || !query.contains("token=")) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }
        String token = null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                token = param.substring("token=".length());
                break;
            }
        }
        if (token == null || token.isEmpty()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }

        try {
            authService.requireAdmin("Bearer " + token);
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid admin token"));
            return;
        }

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!session.isOpen()) {
                    cancelTask(session.getId());
                    return;
                }
                pushOverview(session);
            } catch (Exception ignored) {
                // Session may have closed between check and send
            }
        }, 0, 30, TimeUnit.SECONDS);
        sessionTasks.put(session.getId(), task);
    }

    private void pushOverview(WebSocketSession session) throws Exception {
        int onlineUsers = tcpSessions.onlineCount() + webSocketSessions.onlineCount();
        int todayMessages = count("SELECT COUNT(*) FROM messages WHERE created_at >= CURRENT_DATE");
        int activeUsers = count("SELECT COUNT(DISTINCT sender_id) FROM messages WHERE created_at >= CURRENT_DATE");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "OVERVIEW");
        payload.put("onlineUsers", onlineUsers);
        payload.put("todayMessages", todayMessages);
        payload.put("activeUsers", activeUsers);
        payload.put("timestamp", System.currentTimeMillis());

        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelTask(session.getId());
    }

    private void cancelTask(String sessionId) {
        ScheduledFuture<?> task = sessionTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
