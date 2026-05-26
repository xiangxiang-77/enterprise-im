package com.enterpriseim.server.admin;

import lombok.val;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.enterpriseim.server.config.ImProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AdminAuthService {
    private static final String TOKEN_PREFIX = "admin-token-";

    private final JdbcTemplate jdbcTemplate;
    private final ImProperties properties;
    private final com.enterpriseim.server.auth.TokenService tokenService;

    public AdminAuthService(JdbcTemplate jdbcTemplate, ImProperties properties, com.enterpriseim.server.auth.TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.tokenService = tokenService;
    }

    public AdminPrincipal login(String phone, String password) {
        if (!properties.getAuth().getAdminPassword().equals(password)) {
            throw new ResponseStatusException(UNAUTHORIZED, "管理员账号或密码错误");
        }

        return jdbcTemplate.query("SELECT u.id, u.display_name, r.name\n" +
                "FROM users u\n" +
                "JOIN admin_users au ON au.user_id = u.id\n" +
                "JOIN admin_roles r ON r.id = au.role_id\n" +
                "WHERE u.phone = ? AND au.enabled = TRUE AND u.status = 'active'\n", rs -> {
            if (!rs.next()) {
                throw new ResponseStatusException(UNAUTHORIZED, "管理员账号未找到");
            }
            val userId = rs.getString("id");
            val issued = tokenService.issueAdminToken(userId, rs.getString("name"));
            return new AdminPrincipal(
                    userId,
                    rs.getString("display_name"),
                    rs.getString("name"),
                    issued.token,
                    issued.expiresAt,
                    permissionsForRole(rs.getString("name"))
            );
        }, phone);
    }

    public AdminPrincipal requireAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "需要管理员令牌");
        }

        val rawToken = authorization.substring("Bearer ".length());
        val userId = rawToken.startsWith(TOKEN_PREFIX) ? rawToken.substring(TOKEN_PREFIX.length()) : tokenService.verify(rawToken).subject;
        return jdbcTemplate.query("SELECT u.id, u.display_name, r.name\n" +
                "FROM users u\n" +
                "JOIN admin_users au ON au.user_id = u.id\n" +
                "JOIN admin_roles r ON r.id = au.role_id\n" +
                "WHERE u.id = ? AND au.enabled = TRUE AND u.status = 'active'\n", rs -> {
            if (!rs.next()) {
                throw new ResponseStatusException(UNAUTHORIZED, "管理员令牌无效或已过期");
            }
            return new AdminPrincipal(
                    rs.getString("id"),
                    rs.getString("display_name"),
                    rs.getString("name"),
                    rawToken,
                    Instant.now().plusSeconds(86400).toString(),
                    permissionsForRole(rs.getString("name"))
            );
        }, userId);
    }

    public void requireRole(AdminPrincipal admin, String... roles) {
        if (!Arrays.asList(roles).contains(admin.role())) {
            throw new ResponseStatusException(FORBIDDEN, "管理员角色权限不足");
        }
    }

    public List<String> permissionsForRole(String role) {
        if ("SUPER_ADMIN".equals(role)) {
            return Arrays.asList(
                    "dashboard.read",
                    "organization.read",
                    "organization.write",
                    "users.write",
                    "groups.write",
                    "resources.write",
                    "audit.read",
                    "config.write",
                    "admin.write",
                    "advanced.read"
            );
        }
        if ("OPERATOR_ADMIN".equals(role)) {
            return Arrays.asList(
                    "dashboard.read",
                    "organization.read",
                    "organization.write",
                    "users.write",
                    "groups.write",
                    "resources.write",
                    "config.write",
                    "advanced.read"
            );
        }
        if ("SECURITY_AUDITOR".equals(role)) {
            return Arrays.asList(
                    "dashboard.read",
                    "audit.read",
                    "resources.write",
                    "advanced.read"
            );
        }
        return Arrays.asList("dashboard.read");
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class AdminPrincipal {
    private String userId;
    private String displayName;
    private String role;
    private String token;
    private String expiresAt;
    private List<String> permissions;
}
}
