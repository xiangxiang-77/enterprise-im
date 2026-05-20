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

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AdminAuthService {
    private static final String TOKEN_PREFIX = "admin-token-";

    private final JdbcTemplate jdbcTemplate;
    private final ImProperties properties;

    public AdminAuthService(JdbcTemplate jdbcTemplate, ImProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public AdminPrincipal login(String phone, String password) {
        if (!properties.getAuth().getAdminPassword().equals(password)) {
            throw new ResponseStatusException(UNAUTHORIZED, "invalid admin credentials");
        }

        return jdbcTemplate.query("SELECT u.id, u.display_name, r.name\n" +
                "FROM users u\n" +
                "JOIN admin_users au ON au.user_id = u.id\n" +
                "JOIN admin_roles r ON r.id = au.role_id\n" +
                "WHERE u.phone = ? AND au.enabled = TRUE AND u.status = 'active'\n", rs -> {
            if (!rs.next()) {
                throw new ResponseStatusException(UNAUTHORIZED, "admin user not found");
            }
            val userId = rs.getString("id");
            return new AdminPrincipal(
                    userId,
                    rs.getString("display_name"),
                    rs.getString("name"),
                    TOKEN_PREFIX + userId,
                    Instant.now().plusSeconds(86400).toString()
            );
        }, phone);
    }

    public AdminPrincipal requireAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer " + TOKEN_PREFIX)) {
            throw new ResponseStatusException(UNAUTHORIZED, "admin token required");
        }

        val userId = authorization.substring(("Bearer " + TOKEN_PREFIX).length());
        return jdbcTemplate.query("SELECT u.id, u.display_name, r.name\n" +
                "FROM users u\n" +
                "JOIN admin_users au ON au.user_id = u.id\n" +
                "JOIN admin_roles r ON r.id = au.role_id\n" +
                "WHERE u.id = ? AND au.enabled = TRUE AND u.status = 'active'\n", rs -> {
            if (!rs.next()) {
                throw new ResponseStatusException(UNAUTHORIZED, "admin token invalid");
            }
            return new AdminPrincipal(
                    rs.getString("id"),
                    rs.getString("display_name"),
                    rs.getString("name"),
                    authorization.substring("Bearer ".length()),
                    Instant.now().plusSeconds(86400).toString()
            );
        }, userId);
    }

    public void requireRole(AdminPrincipal admin, String... roles) {
        if (!Arrays.asList(roles).contains(admin.role())) {
            throw new ResponseStatusException(FORBIDDEN, "admin role forbidden");
        }
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
}
}
