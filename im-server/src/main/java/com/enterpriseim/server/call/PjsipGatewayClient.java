package com.enterpriseim.server.call;

import lombok.val;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.enterpriseim.server.config.ImProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class PjsipGatewayClient {
    private final ImProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PjsipGatewayClient(ImProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        val requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getRealtime().getProbeTimeoutMs());
        requestFactory.setReadTimeout(properties.getRealtime().getProbeTimeoutMs());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public MediaGatewayResult create(CallService.CallRecord call) {
        Map<String, String> body = new HashMap<String, String>();
        body.put("callId", call.id());
        body.put("callerId", call.callerId());
        body.put("calleeId", call.calleeId());
        body.put("conversationId", call.conversationId());
        body.put("mediaType", call.mediaType());
        body.put("turnSessionId", call.turnSessionId());
        return post("/api/pjsip/calls", body);
    }

    public MediaGatewayResult answer(CallService.CallRecord call, String actorId) {
        Map<String, String> body = new HashMap<String, String>();
        body.put("actorId", actorId);
        return post("/api/pjsip/calls/" + call.id() + "/answer", body);
    }

    public MediaGatewayResult hangup(CallService.CallRecord call, String actorId) {
        Map<String, String> body = new HashMap<String, String>();
        body.put("actorId", actorId);
        return post("/api/pjsip/calls/" + call.id() + "/hangup", body);
    }

    private MediaGatewayResult post(String path, Map<String, String> body) {
        val baseUrl = properties.getRealtime().getPjsipSignalUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return MediaGatewayResult.unavailable("PJSIP signal url not configured");
        }
        val started = System.nanoTime();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<String>(objectMapper.writeValueAsString(body), headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(baseUrl + path, entity, Map.class);
            val sessionId = response == null ? null : stringValue(response.get("sessionId"));
            val engine = response == null ? null : stringValue(response.get("engine"));
            if ("simulated-pjsip".equals(engine)) {
                return new MediaGatewayResult(false, sessionId, "signaling_only", "PJSIP gateway is running simulated-pjsip; native RTP media is not attached", elapsedMs(started));
            }
            return new MediaGatewayResult(true, sessionId, "media_ready", null, elapsedMs(started));
        } catch (Exception ex) {
            return MediaGatewayResult.unavailable(ex.getClass().getSimpleName() + ": " + ex.getMessage(), elapsedMs(started));
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private long elapsedMs(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class MediaGatewayResult {
    private boolean available;
    private String sessionId;
    private String mediaStatus;
    private String error;
    private long durationMs;

public static MediaGatewayResult unavailable(String error) {
            return unavailable(error, 0);
        }

        public static MediaGatewayResult unavailable(String error, long durationMs) {
            return new MediaGatewayResult(false, null, "signaling_only", error, durationMs);
        }
}
}
