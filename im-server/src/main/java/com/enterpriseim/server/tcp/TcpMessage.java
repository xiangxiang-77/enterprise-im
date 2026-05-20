package com.enterpriseim.server.tcp;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import com.fasterxml.jackson.databind.JsonNode;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class TcpMessage {
    private String version;
    private String type;
    private String requestId;
    private String from;
    private String to;
    private String conversationId;
    private long timestamp;
    private JsonNode payload;

public TcpMessage withType(String nextType) {
        return new TcpMessage(version, nextType, requestId, from, to, conversationId, System.currentTimeMillis(), payload);
    }
}

