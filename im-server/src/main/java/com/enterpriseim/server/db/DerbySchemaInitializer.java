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
                    .replace(" text", " CLOB")
                    .replace("CREATE TABLE IF NOT EXISTS", "CREATE TABLE")
                    .replace("CREATE INDEX IF NOT EXISTS", "CREATE INDEX");
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
                "db/migration/V6__call_media_gateway_state.sql",
                "db/migration/V7__advanced_im_features.sql",
                "db/migration/V8__group_policy_configs.sql",
                "db/migration/V9__notification_and_conversation_settings.sql",
                "db/migration/V10__notification_events.sql",
                "db/migration/V11__file_lifecycle_and_rate_policies.sql",
                "db/migration/V12__auth_provider_boundaries.sql",
                "db/migration/V13__group_advanced_controls.sql",
                "db/migration/V14__push_provider_boundaries.sql",
                "db/migration/V15__file_chunk_upload_and_preview_adapters.sql",
                "db/migration/V16__admin_production_closeout.sql",
                "db/migration/V17__disable_demo_sms_provider.sql",
                "db/migration/V18__department_permissions.sql",
                "db/migration/V19__app_templates.sql",
                "db/migration/V20__app_version_enhancements.sql"
        );
    }

    private void seedDemoAdmin() {
        execute("INSERT INTO enterprises(id, name, code) "
                + "VALUES ('ent_demo', '演示企业', 'DEMO')");
        execute("INSERT INTO departments(id, enterprise_id, parent_id, name, sort_order) "
                + "VALUES ('dept_demo_root', 'ent_demo', NULL, '默认部门', 0)");
        execute("INSERT INTO users(id, enterprise_id, phone, email, display_name, status) "
                + "VALUES ('u_admin_demo', 'ent_demo', '18800000000', 'admin@example.com', '系统管理员', 'active')");
        execute("INSERT INTO admin_users(id, user_id, role_id, enabled) "
                + "VALUES ('admin_demo_super', 'u_admin_demo', 'role_super_admin', TRUE)");
        execute("INSERT INTO department_members(department_id, user_id, position_name) "
                + "VALUES ('dept_demo_root', 'u_admin_demo', '系统管理员')");
        execute("UPDATE admin_roles SET description = '超级管理员' WHERE id = 'role_super_admin'");
        execute("UPDATE admin_roles SET description = '运营管理员' WHERE id = 'role_operator'");
        execute("UPDATE admin_roles SET description = '审计员' WHERE id = 'role_auditor'");
        execute("UPDATE admin_roles SET description = '只读运维' WHERE id = 'role_readonly_ops'");
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
