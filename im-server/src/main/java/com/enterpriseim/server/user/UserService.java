package com.enterpriseim.server.user;

import lombok.val;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final JdbcTemplate jdbcTemplate;

    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureUser(String userId, String displayName, String phone) {
        ensureDefaultDirectory();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE users SET enterprise_id = COALESCE(enterprise_id, 'ent_demo'), updated_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
            ensureDepartmentMember(userId);
            return;
        }

        jdbcTemplate.update("INSERT INTO users(id, enterprise_id, phone, display_name, status)\n" +
                "VALUES (?, 'ent_demo', ?, ?, 'active')\n", userId, phone, displayName == null || displayName.trim().isEmpty() ? userId : displayName);
        ensureDepartmentMember(userId);
    }

    private void ensureDefaultDirectory() {
        jdbcTemplate.update("INSERT INTO enterprises(id, name, code)\n" +
                "SELECT 'ent_demo', '演示企业', 'DEMO'\n" +
                "WHERE NOT EXISTS (SELECT 1 FROM enterprises WHERE id = 'ent_demo')");
        jdbcTemplate.update("INSERT INTO departments(id, enterprise_id, parent_id, name, sort_order)\n" +
                "SELECT 'dept_demo_root', 'ent_demo', NULL, '默认部门', 0\n" +
                "WHERE NOT EXISTS (SELECT 1 FROM departments WHERE id = 'dept_demo_root')");
    }

    private void ensureDepartmentMember(String userId) {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM department_members WHERE department_id = 'dept_demo_root' AND user_id = ?",
                Integer.class, userId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO department_members(department_id, user_id, position_name) VALUES ('dept_demo_root', ?, 'Member')", userId);
    }
}

