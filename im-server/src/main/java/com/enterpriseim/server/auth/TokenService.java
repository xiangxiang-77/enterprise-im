package com.enterpriseim.server.auth;

import com.enterpriseim.server.config.ImProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class TokenService {
    private final ImProperties properties;
    private final ObjectMapper objectMapper;

    public TokenService(ImProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public IssuedToken issueUserToken(String userId) {
        return issue(userId, "user");
    }

    public IssuedToken issueAdminToken(String userId, String role) {
        return issue(userId, "admin:" + role);
    }

    public TokenClaims verify(String token) {
        try {
            val parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("bad jwt shape");
            }
            val signed = parts[0] + "." + parts[1];
            val expected = base64Url(hmac(signed));
            if (!constantTimeEquals(expected, parts[2])) {
                throw new IllegalArgumentException("bad jwt signature");
            }
            val payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(payload);
            val issuer = node.path("iss").asText("");
            val subject = node.path("sub").asText("");
            val scope = node.path("scope").asText("");
            val expiresAt = node.path("exp").asLong(0L);
            if (!properties.getAuth().getJwtIssuer().equals(issuer) || subject.trim().isEmpty() || expiresAt < Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("jwt expired or invalid");
            }
            return new TokenClaims(subject, scope, Instant.ofEpochSecond(expiresAt).toString());
        } catch (Exception e) {
            throw new ResponseStatusException(UNAUTHORIZED, "令牌无效");
        }
    }

    private IssuedToken issue(String subject, String scope) {
        try {
            val expiresAt = Instant.now().plusSeconds(properties.getAuth().getAccessTokenTtlSeconds());
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", properties.getAuth().getJwtIssuer());
            payload.put("sub", subject);
            payload.put("scope", scope);
            payload.put("iat", Instant.now().getEpochSecond());
            payload.put("exp", expiresAt.getEpochSecond());
            val signed = base64Url(objectMapper.writeValueAsBytes(header)) + "." + base64Url(objectMapper.writeValueAsBytes(payload));
            return new IssuedToken(signed + "." + base64Url(hmac(signed)), expiresAt.toString());
        } catch (Exception e) {
            throw new IllegalStateException("failed to issue jwt", e);
        }
    }

    private byte[] hmac(String value) throws Exception {
        val mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) return false;
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    public static class IssuedToken {
        public final String token;
        public final String expiresAt;

        public IssuedToken(String token, String expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    public static class TokenClaims {
        public final String subject;
        public final String scope;
        public final String expiresAt;

        public TokenClaims(String subject, String scope, String expiresAt) {
            this.subject = subject;
            this.scope = scope;
            this.expiresAt = expiresAt;
        }
    }
}
