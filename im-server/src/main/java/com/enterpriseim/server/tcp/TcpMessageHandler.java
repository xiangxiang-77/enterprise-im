package com.enterpriseim.server.tcp;

import lombok.val;

import com.enterpriseim.server.config.ImProperties;
import com.enterpriseim.server.auth.TokenService;
import com.enterpriseim.server.message.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class TcpMessageHandler extends SimpleChannelInboundHandler<String> {
    private final ObjectMapper objectMapper;
    private final OnlineSessionRegistry sessions;
    private final ImProperties properties;
    private final TokenService tokenService;
    private final MessageService messageService;

    public TcpMessageHandler(ObjectMapper objectMapper, OnlineSessionRegistry sessions, ImProperties properties, TokenService tokenService, MessageService messageService) {
        this.objectMapper = objectMapper;
        this.sessions = sessions;
        this.properties = properties;
        this.tokenService = tokenService;
        this.messageService = messageService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String raw) throws Exception {
        val message = objectMapper.readValue(raw, TcpMessage.class);
        switch (message.type()) {
            case "AUTH":
                handleAuth(ctx, message);
                break;
            case "PING":
                write(ctx, response(message, "PONG", payload("onlineCount", sessions.onlineCount())));
                break;
            case "TEXT":
                handleText(ctx, message);
                break;
            case "TYPING":
                handleTyping(ctx, message);
                break;
            case "ACK":
                write(ctx, response(message, "ACK_OK", payload("ack", true)));
                break;
            default:
                write(ctx, response(message, "ERROR", payload("message", "unsupported tcp message type")));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        sessions.remove(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }

    private void handleAuth(ChannelHandlerContext ctx, TcpMessage message) throws Exception {
        val token = message.payload() == null ? "" : message.payload().path("token").asText("");
        val userId = resolveUserId(token);
        sessions.bind(userId, ctx.channel());
        write(ctx, response(message, "AUTH_OK", payload("userId", userId)));
    }

    private String resolveUserId(String token) throws Exception {
        if (token.startsWith(properties.getAuth().getDemoTokenPrefix())) {
            return token.substring(properties.getAuth().getDemoTokenPrefix().length());
        }
        try {
            val claims = tokenService.verify(token);
            if (!"user".equals(claims.scope)) {
                throw new IllegalArgumentException("invalid token scope");
            }
            return claims.subject;
        } catch (Exception e) {
            throw e;
        }
    }

    private void handleText(ChannelHandlerContext ctx, TcpMessage message) throws Exception {
        val persisted = messageService.persistText(message);
        val ackPayload = payload("serverTime", System.currentTimeMillis());
        ackPayload.put("messageId", persisted.messageId());
        write(ctx, response(message, "ACK", ackPayload));
        if (message.to() == null || message.to().trim().isEmpty()) {
            return;
        }
        val deliveryPayload = message.payload() != null && message.payload().isObject()
                ? (ObjectNode) message.payload().deepCopy()
                : objectMapper.createObjectNode();
        deliveryPayload.put("messageId", persisted.messageId());

        val delivery = new TcpMessage(
                message.version(),
                "TEXT_DELIVER",
                message.requestId(),
                message.from(),
                message.to(),
                message.conversationId(),
                System.currentTimeMillis(),
                deliveryPayload
        );
        sessions.findChannel(message.to()).ifPresent(channel -> channel.writeAndFlush(toLine(delivery)));
    }

    private void handleTyping(ChannelHandlerContext ctx, TcpMessage message) throws Exception {
        if (message.to() != null) {
            sessions.findChannel(message.to()).ifPresent(target -> {
                val deliver = new TcpMessage(
                        message.version() == null ? "1" : message.version(),
                        "TYPING_DELIVER",
                        message.requestId(),
                        message.from(),
                        message.to(),
                        message.conversationId(),
                        System.currentTimeMillis(),
                        message.payload()
                );
                target.writeAndFlush(toLine(deliver));
            });
        }
    }

    private TcpMessage response(TcpMessage source, String type, ObjectNode payload) {
        return new TcpMessage(
                source.version() == null ? "1" : source.version(),
                type,
                source.requestId(),
                "server",
                source.from(),
                source.conversationId(),
                System.currentTimeMillis(),
                payload
        );
    }

    private ObjectNode payload(String key, Object value) {
        val node = JsonNodeFactory.instance.objectNode();
        if (value instanceof Boolean) {
            node.put(key, (Boolean) value);
        } else if (value instanceof Integer) {
            node.put(key, (Integer) value);
        } else if (value instanceof Long) {
            node.put(key, (Long) value);
        } else {
            node.put(key, String.valueOf(value));
        }
        return node;
    }

    private void write(ChannelHandlerContext ctx, TcpMessage message) throws Exception {
        ctx.writeAndFlush(toLine(message));
    }

    private String toLine(TcpMessage message) {
        try {
            return objectMapper.writeValueAsString(message) + "\n";
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode tcp message", e);
        }
    }
}
