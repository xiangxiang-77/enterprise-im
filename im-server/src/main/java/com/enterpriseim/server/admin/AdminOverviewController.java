package com.enterpriseim.server.admin;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.tcp.OnlineSessionRegistry;
import com.enterpriseim.server.ws.WebSocketSessionRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {
    private final JdbcTemplate jdbcTemplate;
    private final OnlineSessionRegistry tcpSessions;
    private final WebSocketSessionRegistry webSocketSessions;
    private final AdminAuthService authService;

    public AdminOverviewController(
            JdbcTemplate jdbcTemplate,
            OnlineSessionRegistry tcpSessions,
            WebSocketSessionRegistry webSocketSessions,
            AdminAuthService authService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tcpSessions = tcpSessions;
        this.webSocketSessions = webSocketSessions;
        this.authService = authService;
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewResponse> overview(@org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);
        int enterpriseUsers = count("SELECT COUNT(*) FROM users");
        int onlineUsers = tcpSessions.onlineCount() + webSocketSessions.onlineCount();
        return ApiResponse.ok(new OverviewResponse(
                enterpriseUsers,
                onlineUsers,
                Math.max(enterpriseUsers - onlineUsers, 0),
                count("SELECT COUNT(*) FROM conversations WHERE type = 'single'"),
                count("SELECT COUNT(*) FROM conversations WHERE type = 'group'"),
                count("SELECT COUNT(*) FROM messages WHERE created_at >= CURRENT_DATE"),
                count("SELECT COUNT(*) FROM friend_requests WHERE status = 'pending'"),
                count("SELECT COUNT(*) FROM call_records"),
                count("SELECT COUNT(*) FROM call_records WHERE status = 'ringing'"),
                count("SELECT COUNT(*) FROM call_records WHERE status = 'answered'"),
                count("SELECT COUNT(*) FROM call_records WHERE status IN ('rejected', 'ended') AND answered_at IS NULL")
        ));
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class OverviewResponse {
    private int enterpriseUsers;
    private int onlineUsers;
    private int offlineUsers;
    private int singleConversations;
    private int groupConversations;
    private int todayMessages;
    private int pendingFriendRequests;
    private int totalCalls;
    private int activeCalls;
    private int answeredCalls;
    private int missedCalls;
}
}
