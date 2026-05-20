package com.enterpriseim.server.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Component
public class DerbySchemaInitializer {
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final boolean flywayEnabled;

    public DerbySchemaInitializer(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            @Value("${spring.flyway.enabled:true}") boolean flywayEnabled) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.flywayEnabled = flywayEnabled;
    }

    @PostConstruct
    void initialize() throws Exception {
        if (flywayEnabled || !isDerby()) {
            return;
        }
        for (String path : migrations()) {
            String sql = read(path)
                    .replace(" TEXT", " CLOB")
                    .replace(" text", " CLOB");
            for (String statement : sql.split(";")) {
                execute(statement.trim());
            }
        }
        seedDemoAdmin();
    }

    private boolean isDerby() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("derby");
        }
    }

    private List<String> migrations() {
        return Arrays.asList(
                "db/migration/V1__init_core_schema.sql",
                "db/migration/V6__call_media_gateway_state.sql"
        );
    }

    private void seedDemoAdmin() {
        execute("INSERT INTO users(id, phone, email, display_name, status) "
                + "VALUES ('u_admin_demo', '18800000000', 'admin@example.com', '绯荤粺绠＄悊鍛?, 'active')");
        execute("INSERT INTO admin_users(id, user_id, role_id, enabled) "
                + "VALUES ('admin_demo_super', 'u_admin_demo', 'role_super_admin', TRUE)");
        execute("UPDATE admin_roles SET description = '鍏ㄩ儴鏉冮檺' WHERE id = 'role_super_admin'");
        execute("UPDATE admin_roles SET description = '缁勭粐涓庤繍钀ョ鐞? WHERE id = 'role_operator'");
        execute("UPDATE admin_roles SET description = '娑堟伅瀹¤涓庨闄╂帶鍒? WHERE id = 'role_auditor'");
        execute("UPDATE admin_roles SET description = '鍙杩愮淮' WHERE id = 'role_readonly_ops'");
    }

    private String read(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (java.io.InputStream input = resource.getInputStream()) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void execute(String statement) {
        if (statement.trim().isEmpty()) {
            return;
        }
        try {
            jdbcTemplate.execute(statement);
        } catch (Exception ex) {
            if (!isBenignDerbyBootstrapError(ex)) {
                throw ex;
            }
        }
    }

    private boolean isBenignDerbyBootstrapError(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLException) {
                SQLException sqlException = (SQLException) current;
                String state = sqlException.getSQLState();
                if ("X0Y32".equals(state) || "X0Y68".equals(state) || "23505".equals(state)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
