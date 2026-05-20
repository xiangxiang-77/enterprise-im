package com.enterpriseim.server.auth;

import lombok.val;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.config.ImProperties;
import com.enterpriseim.server.user.UserService;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final ImProperties properties;
    private final UserService userService;

    public AuthController(ImProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        val userId = "u_" + request.phone();
        val displayName = "演示用户";
        userService.ensureUser(userId, displayName, request.phone());
        val token = properties.getAuth().getDemoTokenPrefix() + userId;
        return ApiResponse.ok(new LoginResponse(userId, displayName, token, Instant.now().plusSeconds(86400).toString()));
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
public static class LoginResponse {
    private String userId;
    private String displayName;
    private String token;
    private String expiresAt;
}
}
