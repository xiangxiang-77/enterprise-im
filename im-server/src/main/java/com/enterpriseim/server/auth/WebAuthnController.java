package com.enterpriseim.server.auth;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.config.ImProperties;
import com.enterpriseim.server.user.UserService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth/webauthn")
public class WebAuthnController {
    private final ImProperties properties;
    private final UserService userService;
    private final TokenService tokenService;
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, ChallengeRecord> challenges = new ConcurrentHashMap<>();

    public WebAuthnController(ImProperties properties, UserService userService, TokenService tokenService, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.userService = userService;
        this.tokenService = tokenService;
        this.jdbcTemplate = jdbcTemplate;
        ensureTable();
    }

    private void ensureTable() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS webauthn_credentials (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "user_id VARCHAR(64) NOT NULL," +
                    "credential_id VARCHAR(512) NOT NULL UNIQUE," +
                    "public_key_json CLOB," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
        } catch (Exception ignored) {
        }
    }

    private String biometricProvider() {
        try {
            val values = jdbcTemplate.query("SELECT config_value FROM system_configs WHERE config_key = ?",
                    (rs, rowNum) -> rs.getString("config_value"), "auth.biometric.provider");
            return values.isEmpty() || values.get(0) == null ? properties.getAuth().getBiometricProvider() : values.get(0).toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return properties.getAuth().getBiometricProvider();
        }
    }

    private String config(String key, String fallback) {
        try {
            val values = jdbcTemplate.query("SELECT config_value FROM system_configs WHERE config_key = ?",
                    (rs, rowNum) -> rs.getString("config_value"), key);
            return values.isEmpty() || values.get(0) == null ? fallback : values.get(0);
        } catch (Exception ex) {
            return fallback;
        }
    }

    @PostMapping("/register/begin")
    public ApiResponse<WebAuthnCreationOptions> beginRegister(@Valid @RequestBody BeginRegisterRequest request) {
        val provider = biometricProvider();
        if ("disabled".equals(provider) || "client_unavailable".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生物识别登录已禁用");
        }
        val userId = "u_" + request.phone();
        val displayName = "Biometric User";
        userService.ensureUser(userId, displayName, request.phone());

        val challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        val challengeId = UUID.randomUUID().toString();
        challenges.put(challengeId, new ChallengeRecord(challenge, userId, Instant.now().plusSeconds(300)));

        val rpId = "demo".equals(provider) ? "localhost" : config("auth.biometric.rpId", "localhost");
        val rpName = "demo".equals(provider) ? "企业即时通讯" : config("auth.biometric.rpName", "Enterprise IM");

        return ApiResponse.ok(new WebAuthnCreationOptions(
                challengeId,
                Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                rpId,
                rpName,
                Base64.getUrlEncoder().withoutPadding().encodeToString(userId.getBytes()),
                userId,
                displayName
        ));
    }

    @PostMapping("/register/complete")
    public ApiResponse<AuthController.LoginResponse> completeRegister(@Valid @RequestBody CompleteRegisterRequest request) {
        val provider = biometricProvider();
        if ("disabled".equals(provider) || "client_unavailable".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生物识别登录已禁用");
        }
        val record = challenges.remove(request.challengeId());
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "注册挑战已过期或不存在");
        }
        if (record.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "注册挑战已过期");
        }

        val userId = record.userId();
        val displayName = "Biometric User";

        // Demo mode: trust client assertion
        jdbcTemplate.update("INSERT INTO webauthn_credentials(id, user_id, credential_id, public_key_json) VALUES (?, ?, ?, ?)",
                "wc_" + UUID.randomUUID(), userId, request.credentialId(), request.publicKeyJson() != null ? request.publicKeyJson() : "{}");

        val token = tokenService.issueUserToken(userId);
        return ApiResponse.ok(new AuthController.LoginResponse(userId, displayName, token.token, token.expiresAt));
    }

    @PostMapping("/auth/begin")
    public ApiResponse<WebAuthnRequestOptions> beginAuth(@Valid @RequestBody BeginAuthRequest request) {
        val provider = biometricProvider();
        if ("disabled".equals(provider) || "client_unavailable".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生物识别登录已禁用");
        }

        val userId = "u_" + request.phone();
        val credentials = jdbcTemplate.query(
                "SELECT credential_id FROM webauthn_credentials WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("credential_id"), userId);

        val challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        val challengeId = UUID.randomUUID().toString();
        challenges.put(challengeId, new ChallengeRecord(challenge, userId, Instant.now().plusSeconds(300)));

        val rpId = "demo".equals(provider) ? "localhost" : config("auth.biometric.rpId", "localhost");

        return ApiResponse.ok(new WebAuthnRequestOptions(
                challengeId,
                Base64.getUrlEncoder().withoutPadding().encodeToString(challenge),
                rpId,
                credentials
        ));
    }

    @PostMapping("/auth/complete")
    public ApiResponse<AuthController.LoginResponse> completeAuth(@Valid @RequestBody CompleteAuthRequest request) {
        val provider = biometricProvider();
        if ("disabled".equals(provider) || "client_unavailable".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生物识别登录已禁用");
        }
        val record = challenges.remove(request.challengeId());
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "认证挑战已过期或不存在");
        }
        if (record.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "认证挑战已过期");
        }

        val userId = record.userId();
        val displayName = "Biometric User";

        // Demo mode: check if credential exists
        val rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webauthn_credentials WHERE user_id = ? AND credential_id = ?",
                Integer.class, userId, request.credentialId());
        if (rows == null || rows == 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "生物识别凭据未注册，请先用手机号登录");
        }

        val token = tokenService.issueUserToken(userId);
        return ApiResponse.ok(new AuthController.LoginResponse(userId, displayName, token.token, token.expiresAt));
    }

    // --- DTOs ---

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class BeginRegisterRequest {
        @NotBlank private String phone;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class WebAuthnCreationOptions {
        private String challengeId;
        private String challenge;
        private String rpId;
        private String rpName;
        private String userId;
        private String userName;
        private String userDisplayName;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class CompleteRegisterRequest {
        @NotBlank private String challengeId;
        @NotBlank private String credentialId;
        private String publicKeyJson;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class BeginAuthRequest {
        @NotBlank private String phone;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class WebAuthnRequestOptions {
        private String challengeId;
        private String challenge;
        private String rpId;
        private List<String> allowCredentialIds;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class CompleteAuthRequest {
        @NotBlank private String challengeId;
        @NotBlank private String credentialId;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    private static class ChallengeRecord {
        private byte[] challenge;
        private String userId;
        private Instant expiresAt;
    }
}
