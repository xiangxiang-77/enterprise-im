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

import java.io.File;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {
    private static final long SERVER_START_TIME = System.currentTimeMillis();

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
                count("SELECT COUNT(*) FROM call_records WHERE status IN ('rejected', 'ended') AND answered_at IS NULL"),
                count("SELECT COUNT(DISTINCT sender_id) FROM messages WHERE created_at >= CURRENT_DATE"),
                longCount("SELECT COALESCE(SUM(size_bytes), 0) FROM files"),
                count("SELECT COUNT(*) FROM files"),
                messageTrend(),
                storageBreakdown(),
                riskTrend(),
                permissionMatrix()
        ));
    }

    @GetMapping("/metrics/throughput")
    public ApiResponse<List<ThroughputPoint>> throughput(@org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);
        List<ThroughputPoint> points = jdbcTemplate.query(
                "SELECT CAST(created_at AS DATE) AS day_part, HOUR(created_at) AS hour_part, COUNT(*) AS cnt " +
                "FROM messages WHERE created_at >= DATEADD('HOUR', -24, CURRENT_TIMESTAMP) " +
                "GROUP BY CAST(created_at AS DATE), HOUR(created_at) " +
                "ORDER BY day_part ASC, hour_part ASC",
                (rs, rowNum) -> {
                    String dayStr = String.valueOf(rs.getDate("day_part"));
                    int hour = rs.getInt("hour_part");
                    String hourLabel = dayStr + "T" + String.format("%02d:00:00", hour);
                    return new ThroughputPoint(hourLabel, rs.getInt("cnt"));
                });
        return ApiResponse.ok(points);
    }

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health(@org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);

        String dbStatus;
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbStatus = "connected";
        } catch (Exception e) {
            dbStatus = "error";
        }

        String redisStatus = "not_configured";

        long freeDiskBytes;
        try {
            File currentDrive = new File(".");
            freeDiskBytes = currentDrive.getFreeSpace();
        } catch (Exception e) {
            freeDiskBytes = -1L;
        }

        long uptimeMs = System.currentTimeMillis() - SERVER_START_TIME;

        return ApiResponse.ok(new HealthResponse(dbStatus, redisStatus, freeDiskBytes, uptimeMs, SERVER_START_TIME));
    }

    private List<TrendPoint> messageTrend() {
        return jdbcTemplate.query("SELECT CAST(created_at AS DATE) AS trend_day, COUNT(*) AS count FROM messages GROUP BY CAST(created_at AS DATE) ORDER BY trend_day DESC LIMIT 7",
                (rs, rowNum) -> new TrendPoint(String.valueOf(rs.getDate("trend_day")), rs.getInt("count")));
    }

    private List<StorageSlice> storageBreakdown() {
        return jdbcTemplate.query("SELECT COALESCE(content_type, 'unknown') AS kind, COUNT(*) AS files, COALESCE(SUM(size_bytes), 0) AS bytes FROM files GROUP BY COALESCE(content_type, 'unknown') ORDER BY bytes DESC LIMIT 10",
                (rs, rowNum) -> new StorageSlice(rs.getString("kind"), rs.getInt("files"), rs.getLong("bytes")));
    }

    private List<TrendPoint> riskTrend() {
        return jdbcTemplate.query("SELECT CAST(created_at AS DATE) AS trend_day, COUNT(*) AS count FROM risk_events GROUP BY CAST(created_at AS DATE) ORDER BY trend_day DESC LIMIT 7",
                (rs, rowNum) -> new TrendPoint(String.valueOf(rs.getDate("trend_day")), rs.getInt("count")));
    }

    private List<PermissionRow> permissionMatrix() {
        return Arrays.asList(
                new PermissionRow("SUPER_ADMIN", true, true, true, true, true),
                new PermissionRow("OPERATOR_ADMIN", true, true, true, false, true),
                new PermissionRow("SECURITY_AUDITOR", true, false, false, true, false),
                new PermissionRow("READONLY_OPS", true, false, false, false, false)
        );
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private long longCount(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
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
    private int todayActiveUsers;
    private long totalStorageBytes;
    private int totalFiles;
    private List<TrendPoint> messageTrend;
    private List<StorageSlice> storageBreakdown;
    private List<TrendPoint> riskTrend;
    private List<PermissionRow> permissionMatrix;
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class TrendPoint {
    private String day;
    private int count;
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class StorageSlice {
    private String kind;
    private int files;
    private long bytes;
}

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class PermissionRow {
    private String role;
    private boolean dashboard;
    private boolean organizationWrite;
    private boolean resourceWrite;
    private boolean auditRead;
    private boolean configWrite;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class ThroughputPoint {
        private String hour;
        private int count;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class HealthResponse {
        private String database;
        private String redis;
        private long diskSpace;
        private long uptime;
        private long startTime;
    }
}
