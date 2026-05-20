package com.enterpriseim.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "im.tcp.port=29100",
        "spring.datasource.url=jdbc:h2:mem:context-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class ImServerApplicationTests {
    @Test
    void contextLoads() {
    }
}
