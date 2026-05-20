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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/admin")
public class AdminOrganizationController {
    private final JdbcTemplate jdbcTemplate;
    private final AdminAuthService authService;

    public AdminOrganizationController(JdbcTemplate jdbcTemplate, AdminAuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
    }

    @GetMapping("/enterprises")
    public ApiResponse<List<EnterpriseRow>> enterprises(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "50") int limit
    ) {
        authService.requireAdmin(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        val rows = jdbcTemplate.query("SELECT e.id, e.name, e.code,\n" +
                "       (SELECT COUNT(*) FROM users u WHERE u.enterprise_id = e.id) AS user_count,\n" +
                "       (SELECT COUNT(*) FROM departments d WHERE d.enterprise_id = e.id) AS department_count\n" +
                "FROM enterprises e\n" +
                "ORDER BY e.created_at DESC, e.id ASC\n" +
                "LIMIT ?\n", (rs, rowNum) -> new EnterpriseRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getInt("user_count"),
                rs.getInt("department_count")
        ), boundedLimit);
        return ApiResponse.ok(rows);
    }

    @PostMapping("/enterprises")
    public ApiResponse<EnterpriseRow> createEnterprise(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody EnterpriseRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val name = required(request.name(), "name");
        val code = required(request.code(), "code");
        val enterpriseId = "ent_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO enterprises(id, name, code)\n" +
                "VALUES (?, ?, ?)\n", enterpriseId, name, code);
        auditTyped(admin.userId(), "ENTERPRISE_CREATE", "enterprise", enterpriseId, "code=" + code);
        return ApiResponse.ok(getEnterprise(enterpriseId));
    }

    @GetMapping("/departments")
    public ApiResponse<List<DepartmentRow>> departments(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String enterpriseId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        authService.requireAdmin(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        val sql = new StringBuilder("SELECT d.id, d.enterprise_id, e.name AS enterprise_name, d.parent_id, d.name, d.sort_order,\n" +
                "       (SELECT COUNT(*) FROM department_members dm WHERE dm.department_id = d.id) AS member_count\n" +
                "FROM departments d\n" +
                "JOIN enterprises e ON e.id = d.enterprise_id\n" +
                "WHERE 1 = 1\n");
        val params = new ArrayList<Object>();
        if (enterpriseId != null && !enterpriseId.trim().isEmpty()) {
            sql.append(" AND d.enterprise_id = ?");
            params.add(enterpriseId.trim());
        }
        sql.append(" ORDER BY e.name ASC, d.sort_order ASC, d.name ASC LIMIT ?");
        params.add(boundedLimit);

        val rows = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DepartmentRow(
                rs.getString("id"),
                rs.getString("enterprise_id"),
                rs.getString("enterprise_name"),
                rs.getString("parent_id"),
                rs.getString("name"),
                rs.getInt("sort_order"),
                rs.getInt("member_count")
        ), params.toArray());
        return ApiResponse.ok(rows);
    }

    @PostMapping("/departments")
    public ApiResponse<DepartmentRow> createDepartment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody DepartmentRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val enterpriseId = required(request.enterpriseId(), "enterpriseId");
        val name = required(request.name(), "name");
        ensureEnterprise(enterpriseId);
        if (request.parentId() != null && !request.parentId().trim().isEmpty()) {
            ensureDepartment(request.parentId());
        }

        val departmentId = "dep_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO departments(id, enterprise_id, parent_id, name, sort_order)\n" +
                "VALUES (?, ?, ?, ?, ?)\n", departmentId, enterpriseId, blankToNull(request.parentId()), name, request.sortOrder());
        audit(admin.userId(), "DEPARTMENT_CREATE", departmentId, "name=" + name);
        return ApiResponse.ok(getDepartment(departmentId));
    }

    @PatchMapping("/departments/{departmentId}")
    public ApiResponse<DepartmentRow> updateDepartment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String departmentId,
            @RequestBody DepartmentRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val name = required(request.name(), "name");
        ensureDepartment(departmentId);
        if (request.parentId() != null && !request.parentId().trim().isEmpty()) {
            ensureDepartment(request.parentId());
        }

        jdbcTemplate.update("UPDATE departments\n" +
                "SET parent_id = ?, name = ?, sort_order = ?, updated_at = CURRENT_TIMESTAMP\n" +
                "WHERE id = ?\n", blankToNull(request.parentId()), name, request.sortOrder(), departmentId);
        audit(admin.userId(), "DEPARTMENT_UPDATE", departmentId, "name=" + name);
        return ApiResponse.ok(getDepartment(departmentId));
    }

    @DeleteMapping("/departments/{departmentId}")
    public ApiResponse<DepartmentMutationResponse> deleteDepartment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String departmentId,
            @RequestBody DeleteDepartmentRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        if (!"CONFIRM".equals(request.confirmText())) {
            throw new ResponseStatusException(BAD_REQUEST, "confirmText must be CONFIRM");
        }
        ensureDepartment(departmentId);
        val memberCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM department_members WHERE department_id = ?\n", Integer.class, departmentId);
        if (memberCount != null && memberCount > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "department has members");
        }
        val childCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM departments WHERE parent_id = ?\n", Integer.class, departmentId);
        if (childCount != null && childCount > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "department has child departments");
        }

        jdbcTemplate.update("DELETE FROM departments WHERE id = ?", departmentId);
        audit(admin.userId(), "DEPARTMENT_DELETE", departmentId, "deleted=true");
        return ApiResponse.ok(new DepartmentMutationResponse(departmentId, "deleted"));
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleRow>> roles(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authService.requireAdmin(authorization);
        val rows = jdbcTemplate.query("SELECT r.id, r.name, r.description,\n" +
                "       (SELECT COUNT(*) FROM admin_users au WHERE au.role_id = r.id AND au.enabled = TRUE) AS admin_count\n" +
                "FROM admin_roles r\n" +
                "ORDER BY r.name ASC\n", (rs, rowNum) -> new RoleRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("admin_count")
        ));
        return ApiResponse.ok(rows);
    }

    @GetMapping("/admin-users")
    public ApiResponse<List<AdminUserRow>> adminUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "50") int limit
    ) {
        authService.requireAdmin(authorization);
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        val rows = jdbcTemplate.query("SELECT au.id, au.user_id, u.display_name, au.role_id, r.name AS role_name, au.enabled\n" +
                "FROM admin_users au\n" +
                "JOIN users u ON u.id = au.user_id\n" +
                "JOIN admin_roles r ON r.id = au.role_id\n" +
                "ORDER BY au.created_at DESC, au.id ASC\n" +
                "LIMIT ?\n", (rs, rowNum) -> new AdminUserRow(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("display_name"),
                rs.getString("role_id"),
                rs.getString("role_name"),
                rs.getBoolean("enabled")
        ), boundedLimit);
        return ApiResponse.ok(rows);
    }

    @PostMapping("/admin-users")
    public ApiResponse<AdminUserRow> createAdminUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AdminUserRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN");
        val userId = required(request.userId(), "userId");
        val roleId = required(request.roleId(), "roleId");
        ensureUser(userId);
        ensureRole(roleId);

        val adminUserId = "admin_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO admin_users(id, user_id, role_id, enabled)\n" +
                "VALUES (?, ?, ?, TRUE)\n", adminUserId, userId, roleId);
        auditTyped(admin.userId(), "ADMIN_USER_CREATE", "admin_user", adminUserId, "userId=" + userId + ",roleId=" + roleId);
        return ApiResponse.ok(getAdminUser(adminUserId));
    }

    @PatchMapping("/admin-users/{adminUserId}/enabled")
    public ApiResponse<AdminUserRow> updateAdminUserEnabled(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String adminUserId,
            @RequestBody AdminUserEnabledRequest request
    ) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN");
        if (!"CONFIRM".equals(request.confirmText())) {
            throw new ResponseStatusException(BAD_REQUEST, "confirmText must be CONFIRM");
        }
        ensureAdminUser(adminUserId);
        jdbcTemplate.update("UPDATE admin_users\n" +
                "SET enabled = ?\n" +
                "WHERE id = ?\n", request.enabled(), adminUserId);
        auditTyped(admin.userId(), "ADMIN_USER_ENABLED_UPDATE", "admin_user", adminUserId, "enabled=" + request.enabled());
        return ApiResponse.ok(getAdminUser(adminUserId));
    }

    private DepartmentRow getDepartment(String departmentId) {
        val rows = jdbcTemplate.query("SELECT d.id, d.enterprise_id, e.name AS enterprise_name, d.parent_id, d.name, d.sort_order,\n" +
                "       (SELECT COUNT(*) FROM department_members dm WHERE dm.department_id = d.id) AS member_count\n" +
                "FROM departments d\n" +
                "JOIN enterprises e ON e.id = d.enterprise_id\n" +
                "WHERE d.id = ?\n", (rs, rowNum) -> new DepartmentRow(
                rs.getString("id"),
                rs.getString("enterprise_id"),
                rs.getString("enterprise_name"),
                rs.getString("parent_id"),
                rs.getString("name"),
                rs.getInt("sort_order"),
                rs.getInt("member_count")
        ), departmentId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "department not found");
        }
        return rows.get(0);
    }

    private EnterpriseRow getEnterprise(String enterpriseId) {
        val rows = jdbcTemplate.query("SELECT e.id, e.name, e.code,\n" +
                "       (SELECT COUNT(*) FROM users u WHERE u.enterprise_id = e.id) AS user_count,\n" +
                "       (SELECT COUNT(*) FROM departments d WHERE d.enterprise_id = e.id) AS department_count\n" +
                "FROM enterprises e\n" +
                "WHERE e.id = ?\n", (rs, rowNum) -> new EnterpriseRow(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getInt("user_count"),
                rs.getInt("department_count")
        ), enterpriseId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "enterprise not found");
        }
        return rows.get(0);
    }

    private void ensureEnterprise(String enterpriseId) {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enterprises WHERE id = ?", Integer.class, enterpriseId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "enterprise not found");
        }
    }

    private void ensureUser(String userId) {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "user not found");
        }
    }

    private void ensureRole(String roleId) {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_roles WHERE id = ?", Integer.class, roleId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "role not found");
        }
    }

    private void ensureAdminUser(String adminUserId) {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_users WHERE id = ?", Integer.class, adminUserId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "admin user not found");
        }
    }

    private void ensureDepartment(String departmentId) {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM departments WHERE id = ?", Integer.class, departmentId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "department not found");
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
        auditTyped(operatorId, action, "department", targetId, detail);
    }

    private void auditTyped(String operatorId, String action, String targetType, String targetId, String detail) {
        jdbcTemplate.update("INSERT INTO audit_logs(id, operator_id, action, target_type, target_id, detail)\n" +
                "VALUES (?, ?, ?, ?, ?, ?)\n", "audit_" + UUID.randomUUID(), operatorId, action, targetType, targetId, detail);
    }

    private AdminUserRow getAdminUser(String adminUserId) {
        val rows = jdbcTemplate.query("SELECT au.id, au.user_id, u.display_name, au.role_id, r.name AS role_name, au.enabled\n" +
                "FROM admin_users au\n" +
                "JOIN users u ON u.id = au.user_id\n" +
                "JOIN admin_roles r ON r.id = au.role_id\n" +
                "WHERE au.id = ?\n", (rs, rowNum) -> new AdminUserRow(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("display_name"),
                rs.getString("role_id"),
                rs.getString("role_name"),
                rs.getBoolean("enabled")
        ), adminUserId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "admin user not found");
        }
        return rows.get(0);
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class DepartmentRow {
    private String id;
    private String enterpriseId;
    private String enterpriseName;
    private String parentId;
    private String name;
    private int sortOrder;
    private int memberCount;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class EnterpriseRow {
    private String id;
    private String name;
    private String code;
    private int userCount;
    private int departmentCount;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class EnterpriseRequest {
    private String name;
    private String code;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class DepartmentRequest {
    private String enterpriseId;
    private String parentId;
    private String name;
    private int sortOrder;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class DeleteDepartmentRequest {
    private String confirmText;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class DepartmentMutationResponse {
    private String departmentId;
    private String status;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class RoleRow {
    private String id;
    private String name;
    private String description;
    private int adminCount;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class AdminUserRow {
    private String id;
    private String userId;
    private String displayName;
    private String roleId;
    private String roleName;
    private boolean enabled;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class AdminUserRequest {
    private String userId;
    private String roleId;
}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class AdminUserEnabledRequest {
    private boolean enabled;
    private String confirmText;
}
}
