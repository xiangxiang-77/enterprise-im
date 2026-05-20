package com.enterpriseim.server.message;

import lombok.val;

import com.enterpriseim.server.tcp.TcpMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "im.tcp.port=19006",
        "spring.datasource.url=jdbc:h2:mem:message-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class MessageControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    MessageService messageService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void listsPersistedConversationMessages() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val conversationId = "c_history_api_" + suffix;
        val requestId = "req-history-" + suffix;
        val payload = JsonNodeFactory.instance.objectNode().put("content", "hello history");
        messageService.persistText(new TcpMessage(
                "1",
                "TEXT",
                requestId,
                "u_history_a_" + suffix,
                "u_history_b_" + suffix,
                conversationId,
                System.currentTimeMillis(),
                payload
        ));

        val json = mockMvc.perform(get("/api/conversations/{conversationId}/messages", conversationId).param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.path("success").asBoolean()).isTrue();
        assertThat(root.path("data")).hasSize(1);
        assertThat(root.path("data").get(0).path("conversationId").asText()).isEqualTo(conversationId);
        assertThat(root.path("data").get(0).path("senderId").asText()).isEqualTo("u_history_a_" + suffix);
        assertThat(root.path("data").get(0).path("content").asText()).isEqualTo("hello history");
        assertThat(root.path("data").get(0).path("clientSeq").asText()).isEqualTo(requestId);
    }
}
