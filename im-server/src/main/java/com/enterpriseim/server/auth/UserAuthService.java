package com.enterpriseim.server.auth;

import lombok.val;

import com.enterpriseim.server.config.ImProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class UserAuthService {
    private final ImProperties properties;
    private final TokenService tokenService;

    public UserAuthService(ImProperties properties, TokenService tokenService) {
        this.properties = properties;
        this.tokenService = tokenService;
    }

    public String requireUser(String authorization) {
        val bearerPrefix = "Bearer ";
        if (authorization == null || !authorization.startsWith(bearerPrefix)) {
            throw new ResponseStatusException(UNAUTHORIZED, "需要用户令牌");
        }

        val token = authorization.substring(bearerPrefix.length());
        val demoPrefix = properties.getAuth().getDemoTokenPrefix();
        if (demoPrefix == null || demoPrefix.trim().isEmpty() || !token.startsWith(demoPrefix)) {
            val claims = tokenService.verify(token);
            if (!"user".equals(claims.scope)) {
                throw new ResponseStatusException(UNAUTHORIZED, "用户令牌权限范围无效");
            }
            return claims.subject;
        }
        if (!properties.getAuth().isAcceptDemoTokens()) {
            throw new ResponseStatusException(UNAUTHORIZED, "演示令牌已禁用");
        }
        val userId = token.substring(demoPrefix.length());
        if (userId.trim().isEmpty()) {
            throw new ResponseStatusException(UNAUTHORIZED, "用户令牌无效");
        }
        return userId;
    }

    public void requireSameUser(String authenticatedUserId, String requestedUserId) {
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new ResponseStatusException(FORBIDDEN, "令牌用户不匹配");
        }
    }
}
