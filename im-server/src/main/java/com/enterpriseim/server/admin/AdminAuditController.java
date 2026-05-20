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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {
    private final JdbcTemplate jdbcTemplate;
    private final AdminAuthService authService;

    public AdminAuditController(JdbcTemplate jdbcTemplate, AdminAuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<AuditLogRow>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType
    ) {
        authService.requireAdmin(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        val sql = new StringBuilder("SELECT id, operator_id, action, target_type, target_id, detail, created_at\n" +
                "FROM audit_logs\n" +
                "WHERE 1 = 1\n");
        val params = new ArrayList<Object>();
        if (hasText(operatorId)) {
            sql.append(" AND operator_id = ?");
            params.add(operatorId.trim());
        }
        if (hasText(action)) {
            sql.append(" AND action = ?");
            params.add(action.trim());
        }
        if (hasText(targetType)) {
            sql.append(" AND target_type = ?");
            params.add(targetType.trim());
        }
        sql.append(" ORDER BY created_at DESC, id ASC LIMIT ?");
        params.add(boundedLimit);

        val logs = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AuditLogRow(
                rs.getString("id"),
                rs.getString("operator_id"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("detail"),
                toInstant(rs.getTimestamp("created_at"))
        ), params.toArray());
        return ApiResponse.ok(logs);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class AuditLogRow {
    private String id;
    private String operatorId;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private Instant createdAt;
}
}
