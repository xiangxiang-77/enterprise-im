package com.enterpriseim.server.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "im.tcp.port=19002",
        "server.port=0",
        "spring.datasource.url=jdbc:h2:mem:schema-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class SchemaMigrationTest {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void coreTablesAreCreatedByFlyway() {
        List<String> tables = jdbcTemplate.queryForList("SELECT table_name\n" +
                "FROM information_schema.tables\n" +
                "WHERE table_schema = 'public'\n", String.class);

        assertThat(tables).contains(
                "users",
                "departments",
                "friendships",
                "chat_groups",
                "conversations",
                "messages",
                "message_receipts",
                "files",
                "audit_logs",
                "call_records"
        );
    }

    @Test
    void adminRolesAreSeeded() {
        Integer roleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_roles", Integer.class);
        assertThat(roleCount).isEqualTo(4);
    }
}
