package com.enterpriseim.server.admin;

import lombok.val;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.enterpriseim.server.api.ApiResponse;
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

    public AdminUserController(JdbcTemplate jdbcTemplate, AdminAuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
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
        val sql = new StringBuilder("SELECT u.id, u.enterprise_id, e.name AS enterprise_name, u.phone, u.email, u.display_name, u.status, u.created_at\n" +
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

    @PatchMapping("/{userId}/status")
    public ApiResponse<UserStatusResponse> updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String userId,
            @RequestBody UserStatusRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        if (!"CONFIRM".equals(request.confirmText())) {
            throw new ResponseStatusException(BAD_REQUEST, "confirmText must be CONFIRM");
        }
        if (!Arrays.asList("active", "disabled").contains(request.status())) {
            throw new ResponseStatusException(BAD_REQUEST, "status must be active or disabled");
        }

        int updated = jdbcTemplate.update("UPDATE users\n" +
                "SET status = ?, updated_at = CURRENT_TIMESTAMP\n" +
                "WHERE id = ?\n", request.status(), userId);
        if (updated == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "user not found");
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

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private UserRow getUser(String userId) {
        val rows = jdbcTemplate.query("SELECT u.id, u.enterprise_id, e.name AS enterprise_name, u.phone, u.email, u.display_name, u.status, u.created_at\n" +
                "FROM users u\n" +
                "LEFT JOIN enterprises e ON e.id = u.enterprise_id\n" +
                "WHERE u.id = ?\n", (rs, rowNum) -> new UserRow(
                rs.getString("id"),
                rs.getString("enterprise_id"),
                rs.getString("enterprise_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at"))
        ), userId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "user not found");
        }
        return rows.get(0);
    }

    private void ensureEnterprise(String enterpriseId) {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enterprises WHERE id = ?", Integer.class, enterpriseId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "enterprise not found");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, field + " required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void audit(String operatorId, String action, String targetId, String detail) {
        jdbcTemplate.update("INSERT INTO audit_logs(id, operator_id, action, target_type, target_id, detail)\n" +
                "VALUES (?, ?, ?, 'user', ?, ?)\n", "audit_" + UUID.randomUUID(), operatorId, action, targetId, detail);
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
public static class UserStatusResponse {
    private String userId;
    private String status;
}
}
