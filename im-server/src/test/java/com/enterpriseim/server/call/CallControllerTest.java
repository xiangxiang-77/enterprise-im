package com.enterpriseim.server.call;

import lombok.val;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "im.tcp.port=19005",
        "im.realtime.pjsip-signal-url=http://127.0.0.1:1",
        "spring.datasource.url=jdbc:h2:mem:call-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CallControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void exposesTurnAndPjsipConfig() throws Exception {
        mockMvc.perform(get("/api/calls/config"))
                .andExpect(status().isUnauthorized());

        val json = mockMvc.perform(get("/api/calls/config")
                        .header("Authorization", token("u_call_config")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(json).path("data");
        assertThat(data.path("turnUrl").asText()).startsWith("turn:");
        assertThat(data.path("turnUsername").asText()).isNotBlank();
        assertThat(data.path("pjsipSignalUrl").asText()).startsWith("http");
    }

    @Test
    void exposesAuthenticatedSipMediaConfig() throws Exception {
        mockMvc.perform(get("/api/calls/media-config")
                        .param("userId", "u_media_a")
                        .param("calleeId", "u_media_b"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/calls/media-config")
                        .header("Authorization", token("u_media_intruder"))
                        .param("userId", "u_media_a")
                        .param("calleeId", "u_media_b"))
                .andExpect(status().isForbidden());

        val json = mockMvc.perform(get("/api/calls/media-config")
                        .header("Authorization", token("u_media_a"))
                        .param("userId", "u_media_a")
                        .param("calleeId", "u_media_b"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(json).path("data");
        assertThat(data.path("sipRegistrar").asText()).startsWith("sip:");
        assertThat(data.path("sipUsername").asText()).isEqualTo("u_media_a");
        assertThat(data.path("selfSipUri").asText()).isEqualTo("sip:u_media_a@enterprise-im.local");
        assertThat(data.path("calleeSipUri").asText()).isEqualTo("sip:u_media_b@127.0.0.1:5060");
        assertThat(data.path("turnPassword").asText()).isNotBlank();

        val androidJson = mockMvc.perform(get("/api/calls/media-config")
                        .header("Authorization", token("u_media_a"))
                        .param("userId", "u_media_a")
                        .param("calleeId", "u_media_b")
                        .param("platform", "android"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode androidData = objectMapper.readTree(androidJson).path("data");
        assertThat(androidData.path("sipRegistrar").asText()).isEqualTo("sip:10.200.71.31:5060");
        assertThat(androidData.path("calleeSipUri").asText()).isEqualTo("sip:u_media_b@10.200.71.31:5060");
    }

    @Test
    void exposesCallReadinessWithoutLeakingTurnPassword() throws Exception {
        val json = mockMvc.perform(get("/api/calls/readiness"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(json).path("data");
        assertThat(data.path("ready").asBoolean()).isTrue();
        assertThat(data.path("supportedMediaTypes"))
                .extracting(JsonNode::asText)
                .contains("audio", "video");
        assertThat(data.path("blockers")).isEmpty();
        assertThat(json).doesNotContain("enterprise-im-secret");
    }

    @Test
    void initiatesAnswersHangsUpAndListsCalls() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val callerId = "u_call_a_" + suffix;
        val calleeId = "u_call_b_" + suffix;
        val conversationId = "c_call_" + suffix;

        val createJson = mockMvc.perform(post("/api/calls")
                        .header("Authorization", token(callerId))
                        .contentType("application/json")
                        .content(String.format("{\"callerId\":\"%s\",\"calleeId\":\"%s\",\"conversationId\":\"%s\",\"mediaType\":\"video\"}\n", callerId, calleeId, conversationId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createData = objectMapper.readTree(createJson).path("data");
        val callId = createData.path("id").asText();
        assertThat(createData.path("status").asText()).isEqualTo("ringing");
        assertThat(createData.path("turnSessionId").asText()).startsWith("turn_");
        assertThat(createData.path("mediaStatus").asText()).isEqualTo("signaling_only");
        assertThat(createData.path("mediaError").asText()).isNotBlank();

        val answerJson = mockMvc.perform(post("/api/calls/{callId}/answer", callId)
                        .header("Authorization", token(calleeId))
                        .contentType("application/json")
                        .content(String.format("{\"actorId\":\"%s\"}\n", calleeId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode answerData = objectMapper.readTree(answerJson).path("data");
        assertThat(answerData.path("status").asText()).isEqualTo("answered");
        assertThat(answerData.path("answeredAt").asText()).isNotBlank();
        assertThat(answerData.path("mediaStatus").asText()).isEqualTo("signaling_only");

        val hangupJson = mockMvc.perform(post("/api/calls/{callId}/hangup", callId)
                        .header("Authorization", token(callerId))
                        .contentType("application/json")
                        .content(String.format("{\"actorId\":\"%s\"}\n", callerId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode hangupData = objectMapper.readTree(hangupJson).path("data");
        assertThat(hangupData.path("status").asText()).isEqualTo("ended");
        assertThat(hangupData.path("endedAt").asText()).isNotBlank();

        val listJson = mockMvc.perform(get("/api/calls")
                        .header("Authorization", token(callerId))
                        .param("userId", callerId)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(listJson).path("data").findValuesAsText("id")).contains(callId);

        mockMvc.perform(get("/api/calls")
                        .header("Authorization", token(calleeId))
                        .param("userId", callerId)
                        .param("limit", "20"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidMediaType() throws Exception {
        val suffix = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/calls")
                        .header("Authorization", token("u_call_a_" + suffix))
                        .contentType("application/json")
                        .content(String.format("{\"callerId\":\"u_call_a_%s\",\"calleeId\":\"u_call_b_%s\",\"conversationId\":\"c_call_%s\",\"mediaType\":\"screen\"}\n", suffix, suffix, suffix)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidCallTransitions() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val callerId = "u_call_a_" + suffix;
        val calleeId = "u_call_b_" + suffix;
        val conversationId = "c_call_" + suffix;

        val createJson = mockMvc.perform(post("/api/calls")
                        .header("Authorization", token(callerId))
                        .contentType("application/json")
                        .content(String.format("{\"callerId\":\"%s\",\"calleeId\":\"%s\",\"conversationId\":\"%s\",\"mediaType\":\"audio\"}\n", callerId, calleeId, conversationId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val callId = objectMapper.readTree(createJson).path("data").path("id").asText();

        mockMvc.perform(post("/api/calls/{callId}/answer", callId)
                        .header("Authorization", token(calleeId))
                        .contentType("application/json")
                        .content(String.format("{\"actorId\":\"%s\"}\n", calleeId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/calls/{callId}/reject", callId)
                        .header("Authorization", token(calleeId))
                        .contentType("application/json")
                        .content(String.format("{\"actorId\":\"%s\"}\n", calleeId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/calls/{callId}/answer", callId)
                        .header("Authorization", token(calleeId))
                        .contentType("application/json")
                        .content(String.format("{\"actorId\":\"%s\"}\n", calleeId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/calls/{callId}/hangup", callId)
                        .header("Authorization", token(callerId))
                        .contentType("application/json")
                        .content(String.format("{\"actorId\":\"%s\"}\n", callerId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/calls/{callId}/hangup", callId)
                        .header("Authorization", token(callerId))
                        .contentType("application/json")
                        .content(String.format("{\"actorId\":\"%s\"}\n", callerId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCallTransitionsFromNonParticipants() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val callerId = "u_call_a_" + suffix;
        val calleeId = "u_call_b_" + suffix;
        val conversationId = "c_call_" + suffix;

        val createJson = mockMvc.perform(post("/api/calls")
                        .header("Authorization", token(callerId))
                        .contentType("application/json")
                        .content(String.format("{\"callerId\":\"%s\",\"calleeId\":\"%s\",\"conversationId\":\"%s\",\"mediaType\":\"audio\"}\n", callerId, calleeId, conversationId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val callId = objectMapper.readTree(createJson).path("data").path("id").asText();

        mockMvc.perform(post("/api/calls/{callId}/answer", callId)
                        .header("Authorization", token(callerId))
                        .contentType("application/json")
                        .content(String.format("{\"actorId\":\"%s\"}\n", callerId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/calls/{callId}/hangup", callId)
                        .header("Authorization", token("intruder_" + suffix))
                        .contentType("application/json")
                        .content(String.format("{\"actorId\":\"intruder_%s\"}\n", suffix)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsCallWritesWithoutMatchingUserToken() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val callerId = "u_call_a_" + suffix;
        val calleeId = "u_call_b_" + suffix;
        val conversationId = "c_call_" + suffix;

        mockMvc.perform(post("/api/calls")
                        .contentType("application/json")
                        .content(String.format("{\"callerId\":\"%s\",\"calleeId\":\"%s\",\"conversationId\":\"%s\",\"mediaType\":\"audio\"}\n", callerId, calleeId, conversationId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/calls")
                        .header("Authorization", token(calleeId))
                        .contentType("application/json")
                        .content(String.format("{\"callerId\":\"%s\",\"calleeId\":\"%s\",\"conversationId\":\"%s\",\"mediaType\":\"audio\"}\n", callerId, calleeId, conversationId)))
                .andExpect(status().isForbidden());
    }

    private String token(String userId) {
        return "Bearer demo-token-" + userId;
    }
}
