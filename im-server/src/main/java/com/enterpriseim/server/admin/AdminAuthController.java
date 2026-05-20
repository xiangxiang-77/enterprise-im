package com.enterpriseim.server.admin;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.enterpriseim.server.api.ApiResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminAuthService.AdminPrincipal> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.phone(), request.password()));
    }

    @GetMapping("/me")
    public ApiResponse<AdminAuthService.AdminPrincipal> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(authService.requireAdmin(authorization));
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public static class LoginRequest {
    private @NotBlank String phone;
    private @NotBlank String password;
}
}
