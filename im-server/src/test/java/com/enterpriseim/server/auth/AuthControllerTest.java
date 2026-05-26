package com.enterpriseim.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "im.tcp.port=19012",
        "spring.datasource.url=jdbc:h2:mem:auth-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetProviderDefaults() {
        jdbcTemplate.update("UPDATE system_configs SET config_value = 'disabled' WHERE config_key = 'auth.sms.provider'");
    }

    @Test
    void exposesProviderBoundaries() throws Exception {
        val json = mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        val data = objectMapper.readTree(json).path("data");
        assertThat(data.path("sms").path("enabled").asBoolean()).isFalse();
        assertThat(data.path("sms").path("mode").asText()).isEqualTo("disabled");
        assertThat(data.path("sso").path("enabled").asBoolean()).isFalse();
        assertThat(data.path("biometric").path("enabled").asBoolean()).isFalse();
    }

    @Test
    void sendsStoresConsumesAndRejectsSmsCodeReuse() throws Exception {
        jdbcTemplate.update("UPDATE system_configs SET config_value = 'demo' WHERE config_key = 'auth.sms.provider'");
        val phone = "139" + Math.abs(UUID.randomUUID().toString().hashCode());
        val sendJson = mockMvc.perform(post("/api/auth/sms/send")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\"}\n"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        val code = objectMapper.readTree(sendJson).path("data").path("debugCode").asText();
        assertThat(code).hasSize(6);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_verification_codes WHERE account = ? AND status = 'active'", Integer.class, phone))
                .isEqualTo(1);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"000000\"}\n"))
                .andExpect(status().isUnauthorized());

        val loginJson = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}\n"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(loginJson).path("data").path("token").asText()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_verification_codes WHERE account = ? AND status = 'consumed'", Integer.class, phone))
                .isEqualTo(1);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}\n"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordLoginRequiresConfiguredDemoPassword() throws Exception {
        val phone = "137" + Math.abs(UUID.randomUUID().toString().hashCode());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"bad\"}\n"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"demo123\"}\n"))
                .andExpect(status().isOk());
    }
}
