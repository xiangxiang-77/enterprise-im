package com.enterpriseim.server.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final JdbcTemplate jdbcTemplate;

    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureUser(String userId, String displayName, String phone) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.update("INSERT INTO users(id, phone, display_name, status)\n" +
                "VALUES (?, ?, ?, 'active')\n", userId, phone, displayName == null || displayName.trim().isEmpty() ? userId : displayName);
    }
}

