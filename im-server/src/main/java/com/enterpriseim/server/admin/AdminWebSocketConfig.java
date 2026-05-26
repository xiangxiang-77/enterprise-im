package com.enterpriseim.server.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AdminWebSocketConfig implements WebSocketConfigurer {
    private final AdminWebSocketHandler handler;

    public AdminWebSocketConfig(AdminWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/admin").setAllowedOriginPatterns("*");
    }
}
