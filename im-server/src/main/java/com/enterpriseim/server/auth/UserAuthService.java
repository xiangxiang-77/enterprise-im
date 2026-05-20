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

    public UserAuthService(ImProperties properties) {
        this.properties = properties;
    }

    public String requireUser(String authorization) {
        val bearerPrefix = "Bearer ";
        if (authorization == null || !authorization.startsWith(bearerPrefix)) {
            throw new ResponseStatusException(UNAUTHORIZED, "user token required");
        }

        val token = authorization.substring(bearerPrefix.length());
        val demoPrefix = properties.getAuth().getDemoTokenPrefix();
        if (!token.startsWith(demoPrefix)) {
            throw new ResponseStatusException(UNAUTHORIZED, "invalid user token");
        }
        val userId = token.substring(demoPrefix.length());
        if (userId.trim().isEmpty()) {
            throw new ResponseStatusException(UNAUTHORIZED, "invalid user token");
        }
        return userId;
    }

    public void requireSameUser(String authenticatedUserId, String requestedUserId) {
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new ResponseStatusException(FORBIDDEN, "token user mismatch");
        }
    }
}
