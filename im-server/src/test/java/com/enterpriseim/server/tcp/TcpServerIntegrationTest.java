package com.enterpriseim.server.tcp;

import lombok.val;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "im.tcp.port=29101",
        "spring.datasource.url=jdbc:h2:mem:tcp-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class TcpServerIntegrationTest {
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void authPingAndTextDeliveryWork() throws Exception {
        try (TcpClient a = TcpClient.connect();
             TcpClient b = TcpClient.connect()) {
            a.send("{\"version\":\"1\",\"type\":\"AUTH\",\"requestId\":\"auth-a\",\"from\":\"u_a\",\"timestamp\":1,\"payload\":{\"token\":\"demo-token-u_a\"}}\n");
            b.send("{\"version\":\"1\",\"type\":\"AUTH\",\"requestId\":\"auth-b\",\"from\":\"u_b\",\"timestamp\":1,\"payload\":{\"token\":\"demo-token-u_b\"}}\n");

            assertThat(a.readLine()).contains("\"type\":\"AUTH_OK\"");
            assertThat(b.readLine()).contains("\"type\":\"AUTH_OK\"");

            a.send("{\"version\":\"1\",\"type\":\"PING\",\"requestId\":\"ping-a\",\"from\":\"u_a\",\"timestamp\":2,\"payload\":{}}\n");
            assertThat(a.readLine()).contains("\"type\":\"PONG\"");

            a.send("{\"version\":\"1\",\"type\":\"TEXT\",\"requestId\":\"msg-1\",\"from\":\"u_a\",\"to\":\"u_b\",\"conversationId\":\"c_1\",\"timestamp\":3,\"payload\":{\"content\":\"hello\"}}\n");
            assertThat(a.readLine()).contains("\"type\":\"ACK\"").contains("messageId");
            assertThat(b.readLine()).contains("\"type\":\"TEXT_DELIVER\"").contains("hello");

            Integer storedMessages = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages\n" +
                "WHERE conversation_id = 'c_1' AND sender_id = 'u_a' AND content = 'hello'\n", Integer.class);
            assertThat(storedMessages).isEqualTo(1);
        }
    }

    @Test
    void callEventsDeliverOverTcp() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val callerId = "u_tcp_call_a_" + suffix;
        val calleeId = "u_tcp_call_b_" + suffix;
        val conversationId = "c_tcp_call_" + suffix;

        try (TcpClient caller = TcpClient.connect();
             TcpClient callee = TcpClient.connect()) {
            caller.send(String.format("{\"version\":\"1\",\"type\":\"AUTH\",\"requestId\":\"auth-caller\",\"from\":\"%s\",\"timestamp\":1,\"payload\":{\"token\":\"demo-token-%s\"}}\n", callerId, callerId));
            callee.send(String.format("{\"version\":\"1\",\"type\":\"AUTH\",\"requestId\":\"auth-callee\",\"from\":\"%s\",\"timestamp\":1,\"payload\":{\"token\":\"demo-token-%s\"}}\n", calleeId, calleeId));

            assertThat(caller.readLine()).contains("\"type\":\"AUTH_OK\"");
            assertThat(callee.readLine()).contains("\"type\":\"AUTH_OK\"");

            Map<String, String> createBody = new HashMap<String, String>();
            createBody.put("callerId", callerId);
            createBody.put("calleeId", calleeId);
            createBody.put("conversationId", conversationId);
            createBody.put("mediaType", "video");
            val createJson = restTemplate.postForEntity("/api/calls", authed(callerId, createBody), String.class).getBody();
            assertThat(createJson).contains("\"status\":\"ringing\"");

            assertThat(caller.readLine()).contains("\"type\":\"CALL_UPDATE\"").contains("\"status\":\"ringing\"");
            val invite = callee.readLine();
            assertThat(invite).contains("\"type\":\"CALL_INVITE\"").contains("\"status\":\"ringing\"");
            val callId = invite.replaceFirst(".*\"id\":\"([^\"]+)\".*", "$1");

            Map<String, String> rejectBody = new HashMap<String, String>();
            rejectBody.put("actorId", calleeId);
            restTemplate.postForEntity("/api/calls/" + callId + "/reject", authed(calleeId, rejectBody), String.class);
            assertThat(caller.readLine()).contains("\"type\":\"CALL_UPDATE\"").contains("\"status\":\"rejected\"");
            assertThat(callee.readLine()).contains("\"type\":\"CALL_UPDATE\"").contains("\"status\":\"rejected\"");
        }
    }

    private static final class TcpClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private TcpClient(Socket socket) throws Exception {
            this.socket = socket;
            this.socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        static TcpClient connect() throws Exception {
            return new TcpClient(new Socket("127.0.0.1", 29101));
        }

        void send(String jsonLine) throws Exception {
            writer.write(jsonLine.trim());
            writer.write("\n");
            writer.flush();
        }

        String readLine() throws Exception {
            return reader.readLine();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    private HttpEntity<Map<String, String>> authed(String userId, Map<String, String> body) {
        val headers = new HttpHeaders();
        headers.setBearerAuth("demo-token-" + userId);
        return new HttpEntity<>(body, headers);
    }
}
