package com.enterpriseim.server.auth;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.auth.sms.AliyunSmsProvider;
import com.enterpriseim.server.auth.sms.DemoSmsProvider;
import com.enterpriseim.server.auth.sms.SmsProvider;
import com.enterpriseim.server.auth.sms.TencentSmsProvider;
import com.enterpriseim.server.auth.sso.OidcAuthService;
import com.enterpriseim.server.config.ImProperties;
import com.enterpriseim.server.user.UserService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger LOG = Logger.getLogger(AuthController.class.getName());

    private final ImProperties properties;
    private final UserService userService;
    private final TokenService tokenService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public AuthController(ImProperties properties, UserService userService, TokenService tokenService,
                          JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.userService = userService;
        this.tokenService = tokenService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/providers")
    public ApiResponse<AuthProvidersResponse> providers() {
        return ApiResponse.ok(new AuthProvidersResponse(
                provider("password", !"disabled".equals(passwordProvider()), passwordProvider(),
                        "demo".equals(passwordProvider()) ? "Configured demo password only." : "Password provider configured."),
                provider("sms", !"disabled".equals(smsProvider()), smsProvider(),
                        "demo".equals(smsProvider()) ? "Demo SMS stores code in database and returns debugCode." : "External SMS adapter boundary."),
                provider("sso", !"disabled".equals(ssoProvider()), ssoProvider(), "SSO external provider boundary."),
                provider("biometric", !"disabled".equals(biometricProvider()) && !"client_unavailable".equals(biometricProvider()), biometricProvider(),
                        "demo".equals(biometricProvider()) ? "Demo biometric via WebAuthn. Register from browser with platform authenticator." : "Biometric auth is client-side and unavailable on web backend.")
        ));
    }

    @PostMapping("/sms/send")
    public ApiResponse<SmsCodeResponse> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        val provider = smsProvider();
        if ("disabled".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "短信服务已禁用");
        }
        val code = String.format("%06d", random.nextInt(1_000_000));
        val expiresAt = Instant.now().plusSeconds(smsTtlSeconds());
        jdbcTemplate.update("UPDATE auth_verification_codes SET status = 'superseded' WHERE account = ? AND purpose = 'login' AND status = 'active'",
                request.phone());
        jdbcTemplate.update("INSERT INTO auth_verification_codes(id, account, purpose, provider, code_hash, expires_at) VALUES (?, ?, 'login', ?, ?, ?)",
                "code_" + UUID.randomUUID(), request.phone(), provider, hashCode(request.phone(), code), java.sql.Timestamp.from(expiresAt));

        // Dispatch to the configured SMS provider
        SmsProvider sms = resolveSmsProvider(provider);
        if (sms != null) {
            boolean sent = sms.send(request.phone(), code);
            if (!sent) {
                LOG.log(Level.WARNING, "SMS provider {0} failed to send code to {1}", new Object[]{provider, request.phone()});
            }
        }

        return ApiResponse.ok(new SmsCodeResponse(request.phone(), provider, DateTimeFormatter.ISO_INSTANT.format(expiresAt),
                "demo".equals(provider) ? code : null));
    }

    private SmsProvider resolveSmsProvider(String providerName) {
        if ("demo".equals(providerName)) {
            return new DemoSmsProvider();
        }
        if ("tencent".equals(providerName)) {
            try {
                return new TencentSmsProvider(properties, objectMapper);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to initialize TencentSmsProvider: " + e.getMessage(), e);
                return null;
            }
        }
        if ("aliyun".equals(providerName)) {
            try {
                return new AliyunSmsProvider(properties, objectMapper);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to initialize AliyunSmsProvider: " + e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        val code = trim(request.code());
        val password = trim(request.password());
        if (code != null) {
            verifySmsCode(request.phone(), code);
        } else if (password != null) {
            verifyPassword(password);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码或短信验证码不能为空");
        }

        val userId = "u_" + request.phone();
        val displayName = "Demo User";
        userService.ensureUser(userId, displayName, request.phone());
        val token = tokenService.issueUserToken(userId);
        return ApiResponse.ok(new LoginResponse(userId, displayName, token.token, token.expiresAt));
    }

    @PostMapping("/sso/login")
    public ApiResponse<LoginResponse> ssoLogin(@Valid @RequestBody SsoLoginRequest request) {
        val provider = ssoProvider();
        if ("disabled".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSO登录已禁用");
        }
        val enterpriseCode = trim(request.enterpriseCode());
        if (enterpriseCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "企业代码不能为空");
        }
        val enterprises = jdbcTemplate.query("SELECT id, name FROM enterprises WHERE code = ?",
                (rs, rowNum) -> new EnterpriseRef(rs.getString("id"), rs.getString("name")), enterpriseCode.toUpperCase());
        if (enterprises.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "企业代码无效，未找到对应企业");
        }
        val enterprise = enterprises.get(0);
        val phone = trim(request.phone());
        if (phone != null) {
            verifyPassword(trim(request.password()));
        }
        val userId = phone != null ? "u_" + phone : "sso_" + enterprise.id();
        val displayName = phone != null ? "SSO User" : enterprise.name() + " SSO User";
        userService.ensureUser(userId, displayName, phone != null ? phone : userId);
        jdbcTemplate.update("UPDATE users SET enterprise_id = ? WHERE id = ?", enterprise.id(), userId);
        val token = tokenService.issueUserToken(userId);
        return ApiResponse.ok(new LoginResponse(userId, displayName, token.token, token.expiresAt));
    }

    @GetMapping("/sso/authorize")
    public ApiResponse<SsoAuthorizeResponse> ssoAuthorize(@RequestParam(value = "redirect_uri", defaultValue = "") String redirectUri) {
        val provider = ssoProvider();
        if ("disabled".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSO登录已禁用");
        }
        if ("oidc".equals(provider)) {
            OidcAuthService oidc = new OidcAuthService(properties, objectMapper);
            String authorizationUrl = oidc.generateAuthorizationUrl(
                    redirectUri.isEmpty() ? null : redirectUri);
            return ApiResponse.ok(new SsoAuthorizeResponse(authorizationUrl));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的SSO提供商: " + provider);
    }

    @GetMapping("/sso/callback")
    public ApiResponse<LoginResponse> ssoCallback(@RequestParam("code") String code,
                                                   @RequestParam(value = "state", defaultValue = "") String state,
                                                   @RequestParam(value = "redirect_uri", defaultValue = "") String redirectUri) {
        val provider = ssoProvider();
        if ("disabled".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSO登录已禁用");
        }
        if (!"oidc".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的SSO提供商: " + provider);
        }

        try {
            OidcAuthService oidc = new OidcAuthService(properties, objectMapper);
            OidcAuthService.TokenResult tokens = oidc.exchangeCodeForToken(code,
                    redirectUri.isEmpty() ? null : redirectUri);

            // Validate the ID token
            OidcAuthService.UserInfoResult userInfo;
            if (tokens.idToken != null && !tokens.idToken.isEmpty()) {
                userInfo = oidc.validateIdToken(tokens.idToken);
            } else {
                // Fall back to userinfo endpoint
                userInfo = oidc.extractUserInfo(tokens.accessToken);
            }

            val userId = "sso_" + userInfo.subject;
            val displayName = userInfo.displayName != null ? userInfo.displayName : userInfo.subject;
            val phone = userInfo.email != null ? userInfo.email : null;
            userService.ensureUser(userId, displayName, phone);
            val token = tokenService.issueUserToken(userId);
            return ApiResponse.ok(new LoginResponse(userId, displayName, token.token, token.expiresAt));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "SSO callback error: " + e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SSO认证失败: " + e.getMessage());
        }
    }

    private AuthProviderDto provider(String name, boolean enabled, String mode, String message) {
        return new AuthProviderDto(name, enabled, mode, message);
    }

    private void verifyPassword(String password) {
        val mode = passwordProvider();
        if ("disabled".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码登录已禁用");
        }
        if ("demo".equals(mode) && !password.equals(properties.getAuth().getUserDemoPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "密码错误");
        }
    }

    private void verifySmsCode(String phone, String code) {
        val rows = jdbcTemplate.query("SELECT id, code_hash, expires_at FROM auth_verification_codes WHERE account = ? AND purpose = 'login' AND status = 'active' ORDER BY created_at DESC",
                (rs, rowNum) -> new SmsCodeRecord(rs.getString("id"), rs.getString("code_hash"), rs.getTimestamp("expires_at").toInstant()),
                phone);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "短信验证码未找到");
        }
        val row = rows.get(0);
        if (row.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "短信验证码已过期");
        }
        if (!row.codeHash().equals(hashCode(phone, code))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "短信验证码错误");
        }
        jdbcTemplate.update("UPDATE auth_verification_codes SET status = 'consumed', consumed_at = CURRENT_TIMESTAMP WHERE id = ?", row.id());
    }

    private String passwordProvider() {
        return config("auth.password.provider", "demo").toLowerCase(Locale.ROOT);
    }

    private String smsProvider() {
        return config("auth.sms.provider", properties.getAuth().getSmsProvider()).toLowerCase(Locale.ROOT);
    }

    private String ssoProvider() {
        return config("auth.sso.provider", properties.getAuth().getSsoProvider()).toLowerCase(Locale.ROOT);
    }

    private String biometricProvider() {
        return config("auth.biometric.provider", properties.getAuth().getBiometricProvider()).toLowerCase(Locale.ROOT);
    }

    private long smsTtlSeconds() {
        try {
            return Long.parseLong(config("auth.sms.ttlSeconds", String.valueOf(properties.getAuth().getSmsCodeTtlSeconds())));
        } catch (NumberFormatException ex) {
            return properties.getAuth().getSmsCodeTtlSeconds();
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

    private String trim(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String hashCode(String phone, String code) {
        try {
            val digest = MessageDigest.getInstance("SHA-256");
            val bytes = digest.digest((phone + ":" + code).getBytes(StandardCharsets.UTF_8));
            val builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "验证码加密失败");
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class LoginRequest {
        private @NotBlank String phone;
        private String password;
        private String code;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class SmsCodeRequest {
        private @NotBlank String phone;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class SmsCodeResponse {
        private String phone;
        private String providerMode;
        private String expiresAt;
        private String debugCode;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    private static class SmsCodeRecord {
        private String id;
        private String codeHash;
        private Instant expiresAt;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class LoginResponse {
        private String userId;
        private String displayName;
        private String token;
        private String expiresAt;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class AuthProvidersResponse {
        private AuthProviderDto password;
        private AuthProviderDto sms;
        private AuthProviderDto sso;
        private AuthProviderDto biometric;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class AuthProviderDto {
        private String name;
        private boolean enabled;
        private String mode;
        private String message;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class SsoAuthorizeResponse {
        private String authorizationUrl;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class SsoLoginRequest {
        @NotBlank private String enterpriseCode;
        private String phone;
        private String password;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    private static class EnterpriseRef {
        private String id;
        private String name;
    }
}
