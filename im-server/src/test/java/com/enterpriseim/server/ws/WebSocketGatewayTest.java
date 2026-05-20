package com.enterpriseim.server.ws;

import lombok.val;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "im.tcp.port=19003",
        "spring.datasource.url=jdbc:h2:mem:ws-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class WebSocketGatewayTest {
    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    TestRestTemplate restTemplate;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void authPingAndTextDeliveryWorkOverWebSocket() throws Exception {
        val a = new QueueingWebSocketHandler();
        val b = new QueueingWebSocketHandler();
        val client = new StandardWebSocketClient();

        WebSocketSession sessionA = client.doHandshake(a, "ws://127.0.0.1:" + port + "/ws/im").get(5, TimeUnit.SECONDS);
        WebSocketSession sessionB = client.doHandshake(b, "ws://127.0.0.1:" + port + "/ws/im").get(5, TimeUnit.SECONDS);

        sessionA.sendMessage(new TextMessage("{\"version\":\"1\",\"type\":\"AUTH\",\"requestId\":\"auth-a\",\"from\":\"u_a\",\"timestamp\":1,\"payload\":{\"token\":\"demo-token-u_a\"}}\n"));
        sessionB.sendMessage(new TextMessage("{\"version\":\"1\",\"type\":\"AUTH\",\"requestId\":\"auth-b\",\"from\":\"u_b\",\"timestamp\":1,\"payload\":{\"token\":\"demo-token-u_b\"}}\n"));

        assertThat(a.take()).contains("\"type\":\"AUTH_OK\"");
        assertThat(b.take()).contains("\"type\":\"AUTH_OK\"");

        sessionA.sendMessage(new TextMessage("{\"version\":\"1\",\"type\":\"TEXT\",\"requestId\":\"msg-1\",\"from\":\"u_a\",\"to\":\"u_b\",\"conversationId\":\"c_1\",\"timestamp\":3,\"payload\":{\"content\":\"hello-ws\"}}\n"));

        assertThat(a.take()).contains("\"type\":\"ACK\"").contains("messageId");
        assertThat(b.take()).contains("\"type\":\"TEXT_DELIVER\"").contains("hello-ws");

        Integer storedMessages = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages\n" +
                "WHERE conversation_id = 'c_1' AND sender_id = 'u_a' AND content = 'hello-ws'\n", Integer.class);
        assertThat(storedMessages).isEqualTo(1);

        sessionA.close();
        sessionB.close();
    }

    @Test
    void callEventsDeliverOverWebSocket() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val callerId = "u_ws_call_a_" + suffix;
        val calleeId = "u_ws_call_b_" + suffix;
        val conversationId = "c_ws_call_" + suffix;
        val caller = new QueueingWebSocketHandler();
        val callee = new QueueingWebSocketHandler();
        val client = new StandardWebSocketClient();

        WebSocketSession callerSession = client.doHandshake(caller, "ws://127.0.0.1:" + port + "/ws/im").get(5, TimeUnit.SECONDS);
        WebSocketSession calleeSession = client.doHandshake(callee, "ws://127.0.0.1:" + port + "/ws/im").get(5, TimeUnit.SECONDS);

        callerSession.sendMessage(new TextMessage(String.format("{\"version\":\"1\",\"type\":\"AUTH\",\"requestId\":\"auth-caller\",\"from\":\"%s\",\"timestamp\":1,\"payload\":{\"token\":\"demo-token-%s\"}}\n", callerId, callerId)));
        calleeSession.sendMessage(new TextMessage(String.format("{\"version\":\"1\",\"type\":\"AUTH\",\"requestId\":\"auth-callee\",\"from\":\"%s\",\"timestamp\":1,\"payload\":{\"token\":\"demo-token-%s\"}}\n", calleeId, calleeId)));

        assertThat(caller.take()).contains("\"type\":\"AUTH_OK\"");
        assertThat(callee.take()).contains("\"type\":\"AUTH_OK\"");

        Map<String, String> createBody = new HashMap<String, String>();
        createBody.put("callerId", callerId);
        createBody.put("calleeId", calleeId);
        createBody.put("conversationId", conversationId);
        createBody.put("mediaType", "audio");
        val createJson = restTemplate.postForEntity("/api/calls", authed(callerId, createBody), String.class).getBody();
        val callId = objectMapper.readTree(createJson).path("data").path("id").asText();

        assertThat(caller.take()).contains("\"type\":\"CALL_UPDATE\"").contains(callId).contains("\"status\":\"ringing\"");
        assertThat(callee.take()).contains("\"type\":\"CALL_INVITE\"").contains(callId).contains("\"status\":\"ringing\"");

        Map<String, String> answerBody = new HashMap<String, String>();
        answerBody.put("actorId", calleeId);
        restTemplate.postForEntity("/api/calls/" + callId + "/answer", authed(calleeId, answerBody), String.class);
        assertThat(caller.take()).contains("\"type\":\"CALL_UPDATE\"").contains(callId).contains("\"status\":\"answered\"");
        assertThat(callee.take()).contains("\"type\":\"CALL_UPDATE\"").contains(callId).contains("\"status\":\"answered\"");

        callerSession.close();
        calleeSession.close();
    }

    private static final class QueueingWebSocketHandler extends org.springframework.web.socket.handler.TextWebSocketHandler {
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        String take() throws Exception {
            return messages.poll(5, TimeUnit.SECONDS);
        }
    }

    private HttpEntity<Map<String, String>> authed(String userId, Map<String, String> body) {
        val headers = new HttpHeaders();
        headers.setBearerAuth("demo-token-" + userId);
        return new HttpEntity<>(body, headers);
    }
}
