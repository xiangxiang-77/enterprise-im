package com.enterpriseim.server.admin;

import lombok.val;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final JdbcTemplate jdbcTemplate;
    private final AdminAuthService authService;
    private final OnlineSessionRegistry tcpSessions;
    private final WebSocketSessionRegistry webSocketSessions;

    public AdminUserController(JdbcTemplate jdbcTemplate, AdminAuthService authService, OnlineSessionRegistry tcpSessions, WebSocketSessionRegistry webSocketSessions) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.tcpSessions = tcpSessions;
        this.webSocketSessions = webSocketSessions;
    }

    @GetMapping
    public ApiResponse<List<UserRow>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String enterpriseId
    ) {
        authService.requireAdmin(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        val sql = new StringBuilder("SELECT u.id, u.enterprise_id, e.name AS enterprise_name, u.phone, u.email, u.display_name, u.avatar_url, u.short_no, u.gender, u.signature, u.status, u.created_at,\n" +
                "       (SELECT dm.position_name FROM department_members dm WHERE dm.user_id = u.id ORDER BY dm.department_id ASC LIMIT 1) AS position_name\n" +
                "FROM users u\n" +
                "LEFT JOIN enterprises e ON e.id = u.enterprise_id\n" +
                "WHERE 1 = 1\n");
        val params = new ArrayList<Object>();
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND u.status = ?");
            params.add(status.trim());
        }
        if (enterpriseId != null && !enterpriseId.trim().isEmpty()) {
            sql.append(" AND u.enterprise_id = ?");
            params.add(enterpriseId.trim());
        }
        sql.append(" ORDER BY u.created_at DESC, u.id ASC LIMIT ?");
        params.add(boundedLimit);

        val users = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new UserRow(
                rs.getString("id"),
                rs.getString("enterprise_id"),
                rs.getString("enterprise_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                rs.getString("short_no"),
                rs.getString("gender"),
                rs.getString("signature"),
                rs.getString("position_name"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at"))
        ), params.toArray());
        return ApiResponse.ok(users);
    }

    @PostMapping
    public ApiResponse<UserRow> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UserCreateRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val displayName = required(request.displayName(), "displayName");
        val phone = required(request.phone(), "phone");
        val enterpriseId = blankToNull(request.enterpriseId());
        if (enterpriseId != null) {
            ensureEnterprise(enterpriseId);
        }
        val userId = "u_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users(id, enterprise_id, phone, email, display_name, status)\n" +
                "VALUES (?, ?, ?, ?, ?, 'active')\n", userId, enterpriseId, phone, blankToNull(request.email()), displayName);
        audit(admin.userId(), "USER_CREATE", userId, "phone=" + phone);
        return ApiResponse.ok(getUser(userId));
    }

    @PostMapping("/batch-import")
    public ApiResponse<BatchImportResult> batchImport(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody BatchImportRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val rows = request.users() == null ? new ArrayList<UserCreateRequest>() : request.users();
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "缺少 users 字段");
        }
        if (rows.size() > 200) {
            throw new ResponseStatusException(BAD_REQUEST, "批量导入限制为 200 个用户");
        }
        int created = 0;
        int skipped = 0;
        val errors = new ArrayList<String>();
        for (UserCreateRequest row : rows) {
            try {
                val phone = required(row.phone(), "phone");
                val displayName = required(row.displayName(), "displayName");
                val enterpriseId = blankToNull(row.enterpriseId());
                if (enterpriseId != null) ensureEnterprise(enterpriseId);
                if (exists("SELECT COUNT(*) FROM users WHERE phone = ?", phone)) {
                    skipped++;
                    errors.add(phone + ": 手机号重复");
                    continue;
                }
                val userId = "u_" + UUID.randomUUID();
                jdbcTemplate.update("INSERT INTO users(id, enterprise_id, phone, email, display_name, status) VALUES (?, ?, ?, ?, ?, 'active')",
                        userId, enterpriseId, phone, blankToNull(row.email()), displayName);
                created++;
            } catch (ResponseStatusException ex) {
                skipped++;
                errors.add(valueOr(row.phone(), "unknown") + ": " + ex.getReason());
            }
        }
        audit(admin.userId(), "USER_BATCH_IMPORT", "batch", "created=" + created + ", skipped=" + skipped);
        return ApiResponse.ok(new BatchImportResult(created, skipped, rows.size(), errors));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<UserStatusResponse> updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String userId,
            @RequestBody UserStatusRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        if (!"CONFIRM".equals(request.confirmText())) {
            throw new ResponseStatusException(BAD_REQUEST, "确认文本必须为 CONFIRM");
        }
        if (!Arrays.asList("active", "disabled").contains(request.status())) {
            throw new ResponseStatusException(BAD_REQUEST, "用户状态必须为 active 或 disabled");
        }

        int updated = jdbcTemplate.update("UPDATE users\n" +
                "SET status = ?, updated_at = CURRENT_TIMESTAMP\n" +
                "WHERE id = ?\n", request.status(), userId);
        if (updated == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "用户未找到");
        }

        jdbcTemplate.update("INSERT INTO audit_logs(id, operator_id, action, target_type, target_id, detail)\n" +
                "VALUES (?, ?, ?, 'user', ?, ?)\n",
                "audit_" + UUID.randomUUID(),
                admin.userId(),
                "USER_STATUS_UPDATE",
                userId,
                "status=" + request.status()
        );

        return ApiResponse.ok(new UserStatusResponse(userId, request.status()));
    }

    @PatchMapping("/{userId}/profile")
    public ApiResponse<UserRow> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String userId,
            @RequestBody UserProfileRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        getUser(userId);
        jdbcTemplate.update("UPDATE users SET display_name = COALESCE(?, display_name), email = ?, avatar_url = ?, short_no = ?, gender = ?, signature = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                blankToNull(request.displayName()), blankToNull(request.email()), blankToNull(request.avatarUrl()), blankToNull(request.shortNo()), blankToNull(request.gender()), blankToNull(request.signature()), userId);
        if (request.positionName() != null) {
            val position = blankToNull(request.positionName());
            if (exists("SELECT COUNT(*) FROM department_members WHERE user_id = ?", userId)) {
                jdbcTemplate.update("UPDATE department_members SET position_name = ? WHERE user_id = ?", position, userId);
            } else {
                val enterpriseId = getUser(userId).enterpriseId();
                val departmentId = firstDepartmentId(enterpriseId);
                if (departmentId != null) {
                    jdbcTemplate.update("INSERT INTO department_members(department_id, user_id, position_name) VALUES (?, ?, ?)", departmentId, userId, position);
                }
            }
        }
        audit(admin.userId(), "USER_PROFILE_UPDATE", userId, "displayName=" + valueOr(request.displayName(), ""));
        return ApiResponse.ok(getUser(userId));
    }

    @PostMapping("/{userId}/force-offline")
    public ApiResponse<ForceOfflineResponse> forceOffline(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String userId,
            @RequestBody ConfirmRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        if (!"CONFIRM".equals(request.confirmText())) {
            throw new ResponseStatusException(BAD_REQUEST, "确认文本必须为 CONFIRM");
        }
        val tcpClosed = tcpSessions.disconnect(userId);
        val wsClosed = webSocketSessions.disconnect(userId);
        audit(admin.userId(), "USER_FORCE_OFFLINE", userId, "tcp=" + tcpClosed + ", ws=" + wsClosed);
        return ApiResponse.ok(new ForceOfflineResponse(userId, tcpClosed, wsClosed));
    }

    @GetMapping("/{userId}/device-sessions")
    public ApiResponse<List<DeviceSessionRow>> deviceSessions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String userId
    ) {
        authService.requireAdmin(authorization);
        getUser(userId);
        val rows = jdbcTemplate.query("SELECT id, user_id, device_type, device_name, online, last_seen_at FROM device_sessions WHERE user_id = ? ORDER BY online DESC, last_seen_at DESC",
                (rs, rowNum) -> new DeviceSessionRow(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("id"),
                        rs.getString("device_type"),
                        rs.getString("device_name"),
                        null,
                        null,
                        rs.getBoolean("online"),
                        toInstant(rs.getTimestamp("last_seen_at"))
                ),
                userId);
        return ApiResponse.ok(rows);
    }

    @GetMapping("/device-policies")
    public ApiResponse<List<DevicePolicyRow>> devicePolicies(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authService.requireAdmin(authorization);
        ensureDevicePolicyDefaults();
        return ApiResponse.ok(jdbcTemplate.query("SELECT config_key, config_value, updated_at FROM system_configs WHERE config_key LIKE 'device.%' ORDER BY config_key",
                (rs, rowNum) -> new DevicePolicyRow(rs.getString("config_key"), rs.getString("config_value"), toInstant(rs.getTimestamp("updated_at")))));
    }

    @PatchMapping("/device-policies/{key}")
    public ApiResponse<DevicePolicyRow> updateDevicePolicy(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String key,
            @RequestBody DevicePolicyRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val configKey = key.startsWith("device.") ? key : "device." + key;
        if (!Arrays.asList("device.maxOnlineDevices", "device.allowUnknownDevice", "device.forceOfflineEnabled").contains(configKey)) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持的设备策略");
        }
        upsertConfig(configKey, required(request.value(), "value"));
        audit(admin.userId(), "DEVICE_POLICY_UPDATE", configKey, request.value());
        return ApiResponse.ok(devicePolicy(configKey));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private UserRow getUser(String userId) {
        val rows = jdbcTemplate.query("SELECT u.id, u.enterprise_id, e.name AS enterprise_name, u.phone, u.email, u.display_name, u.avatar_url, u.short_no, u.gender, u.signature, u.status, u.created_at,\n" +
                "       (SELECT dm.position_name FROM department_members dm WHERE dm.user_id = u.id ORDER BY dm.department_id ASC LIMIT 1) AS position_name\n" +
                "FROM users u\n" +
                "LEFT JOIN enterprises e ON e.id = u.enterprise_id\n" +
                "WHERE u.id = ?\n", (rs, rowNum) -> new UserRow(
                rs.getString("id"),
                rs.getString("enterprise_id"),
                rs.getString("enterprise_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                rs.getString("short_no"),
                rs.getString("gender"),
                rs.getString("signature"),
                rs.getString("position_name"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at"))
        ), userId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "用户未找到");
        }
        return rows.get(0);
    }

    private void ensureEnterprise(String enterpriseId) {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enterprises WHERE id = ?", Integer.class, enterpriseId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "企业未找到");
        }
    }

    private String firstDepartmentId(String enterpriseId) {
        if (enterpriseId == null) return null;
        val rows = jdbcTemplate.queryForList("SELECT id FROM departments WHERE enterprise_id = ? ORDER BY sort_order ASC, id ASC LIMIT 1", String.class, enterpriseId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean exists(String sql, Object... params) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, field + " 不能为空");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void audit(String operatorId, String action, String targetId, String detail) {
        jdbcTemplate.update("INSERT INTO audit_logs(id, operator_id, action, target_type, target_id, detail)\n" +
                "VALUES (?, ?, ?, 'user', ?, ?)\n", "audit_" + UUID.randomUUID(), operatorId, action, targetId, detail);
    }

    private void upsertConfig(String key, String value) {
        if (exists("SELECT COUNT(*) FROM system_configs WHERE config_key = ?", key)) {
            jdbcTemplate.update("UPDATE system_configs SET config_value = ?, updated_at = CURRENT_TIMESTAMP WHERE config_key = ?", value, key);
        } else {
            jdbcTemplate.update("INSERT INTO system_configs(config_key, config_value) VALUES (?, ?)", key, value);
        }
    }

    private void ensureDevicePolicyDefaults() {
        if (!exists("SELECT COUNT(*) FROM system_configs WHERE config_key = 'device.maxOnlineDevices'")) upsertConfig("device.maxOnlineDevices", "5");
        if (!exists("SELECT COUNT(*) FROM system_configs WHERE config_key = 'device.allowUnknownDevice'")) upsertConfig("device.allowUnknownDevice", "true");
        if (!exists("SELECT COUNT(*) FROM system_configs WHERE config_key = 'device.forceOfflineEnabled'")) upsertConfig("device.forceOfflineEnabled", "true");
    }

    private DevicePolicyRow devicePolicy(String key) {
        return jdbcTemplate.query("SELECT config_key, config_value, updated_at FROM system_configs WHERE config_key = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "设备策略未找到");
            return new DevicePolicyRow(rs.getString("config_key"), rs.getString("config_value"), toInstant(rs.getTimestamp("updated_at")));
        }, key);
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class UserRow {
    private String id;
    private String enterpriseId;
    private String enterpriseName;
    private String phone;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String shortNo;
    private String gender;
    private String signature;
    private String positionName;
    private String status;
    private Instant createdAt;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class UserStatusRequest {
    private String status;
    private String confirmText;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class UserCreateRequest {
    private String enterpriseId;
    private String phone;
    private String email;
    private String displayName;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class UserProfileRequest {
    private String displayName;
    private String email;
    private String avatarUrl;
    private String shortNo;
    private String gender;
    private String signature;
    private String positionName;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class BatchImportRequest {
    private ArrayList<UserCreateRequest> users;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class BatchImportResult {
    private int created;
    private int skipped;
    private int total;
    private List<String> errors;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class UserStatusResponse {
    private String userId;
    private String status;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class ConfirmRequest {
    private String confirmText;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class ForceOfflineResponse {
    private String userId;
    private boolean tcpClosed;
    private boolean websocketClosed;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class DevicePolicyRow {
    private String key;
    private String value;
    private Instant updatedAt;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class DevicePolicyRequest {
    private String value;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class DeviceSessionRow {
    private String id;
    private String userId;
    private String deviceId;
    private String deviceType;
    private String deviceName;
    private String ipAddress;
    private String userAgent;
    private boolean online;
    private Instant lastSeenAt;
}
}
