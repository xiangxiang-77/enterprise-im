package com.enterpriseim.server.call;

import com.enterpriseim.server.config.ImProperties;
import com.enterpriseim.server.user.UserService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class CallService {
    private final JdbcTemplate jdbcTemplate;
    private final UserService userService;
    private final ImProperties properties;
    private final CallRealtimeNotifier realtimeNotifier;
    private final PjsipGatewayClient pjsipGatewayClient;

    public CallService(JdbcTemplate jdbcTemplate, UserService userService, ImProperties properties,
                       CallRealtimeNotifier realtimeNotifier, PjsipGatewayClient pjsipGatewayClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.userService = userService;
        this.properties = properties;
        this.realtimeNotifier = realtimeNotifier;
        this.pjsipGatewayClient = pjsipGatewayClient;
    }

    public CallConfig config() {
        val realtime = properties.getRealtime();
        return new CallConfig(realtime.getTurnUrl(), realtime.getTurnUsername(), realtime.getTurnPassword(),
                realtime.getPjsipSignalUrl());
    }

    public MediaConfig mediaConfig(String userId, String calleeId, String platform) {
        val realtime = properties.getRealtime();
        val currentUserId = required(userId, "userId");
        val targetUserId = calleeId == null || calleeId.trim().isEmpty() ? null : calleeId.trim();
        val nativeClient = "android".equalsIgnoreCase(platform) || "desktop".equalsIgnoreCase(platform);
        val registrar = "android".equalsIgnoreCase(platform)
                && realtime.getSipAndroidRegistrar() != null
                && !realtime.getSipAndroidRegistrar().trim().isEmpty()
                ? realtime.getSipAndroidRegistrar()
                : realtime.getSipRegistrar();
        val calleeSipUri = targetUserId == null ? null : (nativeClient
                ? sipUri(targetUserId, sipHost(registrar))
                : sipUri(targetUserId, realtime.getSipDomain()));
        return new MediaConfig(
                realtime.getSipDomain(),
                registrar,
                realtime.getSipRealm(),
                currentUserId,
                realtime.getSipPassword(),
                sipUri(currentUserId, realtime.getSipDomain()),
                calleeSipUri,
                realtime.getTurnUrl(),
                realtime.getTurnUsername(),
                realtime.getTurnPassword()
        );
    }

    public CallReadiness readiness() {
        val realtime = properties.getRealtime();
        val checks = new ArrayList<ReadinessCheck>();
        checks.add(check("turnUrl", realtime.getTurnUrl(), "TURN 地址未配置"));
        checks.add(check("turnUsername", realtime.getTurnUsername(), "TURN 用户名未配置"));
        checks.add(check("turnPassword", realtime.getTurnPassword(), "TURN 密码未配置"));
        checks.add(check("pjsipSignalUrl", realtime.getPjsipSignalUrl(), "PJSIP 信令地址未配置"));

        val ready = checks.stream().allMatch(ReadinessCheck::ready);
        val blockers = checks.stream()
                .filter(item -> !item.ready())
                .map(ReadinessCheck::message)
                .collect(Collectors.toList());
        return new CallReadiness(ready, Arrays.asList("audio", "video"), checks, blockers);
    }

    public CallConnectivity connectivity() {
        val realtime = properties.getRealtime();
        val checks = Arrays.asList(
                socketProbe("turn", realtime.getTurnUrl(), 3478, realtime.getProbeTimeoutMs()),
                socketProbe("pjsipSignal", realtime.getPjsipSignalUrl(), 80, realtime.getProbeTimeoutMs())
        );
        return new CallConnectivity(checks.stream().allMatch(ConnectivityCheck::reachable), checks);
    }

    @Transactional
    public CallRecord initiate(InitiateCallRequest request) {
        val callerId = required(request.callerId(), "callerId");
        val calleeId = required(request.calleeId(), "calleeId");
        val conversationId = required(request.conversationId(), "conversationId");
        val mediaType = normalizeMediaType(request.mediaType());
        val callId = "call_" + UUID.randomUUID();
        val turnSessionId = "turn_" + UUID.randomUUID();

        userService.ensureUser(callerId, callerId, null);
        userService.ensureUser(calleeId, calleeId, null);
        ensureConversation(conversationId, "single", calleeId);
        ensureConversationMember(conversationId, callerId);
        ensureConversationMember(conversationId, calleeId);

        jdbcTemplate.update("INSERT INTO call_records(id, conversation_id, caller_id, callee_id, media_type, status, turn_session_id)\n" +
                "VALUES (?, ?, ?, ?, ?, 'ringing', ?)\n", callId, conversationId, callerId, calleeId, mediaType, turnSessionId);
        CallRecord record = get(callId);
        applyGatewayResult(callId, pjsipGatewayClient.create(record));
        record = get(callId);
        realtimeNotifier.notifyCallStarted(record);
        return record;
    }

    public CallRecord answer(String callId, TransitionCallRequest request) {
        val actorId = required(request.actorId(), "actorId");
        return transition(callId, "answered", actorId);
    }

    public CallRecord reject(String callId, TransitionCallRequest request) {
        val actorId = required(request.actorId(), "actorId");
        return transition(callId, "rejected", actorId);
    }

    public CallRecord hangup(String callId, TransitionCallRequest request) {
        val actorId = required(request.actorId(), "actorId");
        return transition(callId, "ended", actorId);
    }

    public List<CallRecord> listByUser(String userId, int limit) {
        val boundedLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("SELECT id, conversation_id, caller_id, callee_id, group_id, media_type, status,\n" +
                "       started_at, answered_at, ended_at, turn_session_id, pjsip_session_id, media_status, media_error\n" +
                "FROM call_records\n" +
                "WHERE caller_id = ? OR callee_id = ?\n" +
                "ORDER BY started_at DESC, id ASC\n" +
                "LIMIT ?\n", (rs, rowNum) -> mapCall(rs), userId, userId, boundedLimit);
    }

    public CallRecord get(String callId) {
        val calls = jdbcTemplate.query("SELECT id, conversation_id, caller_id, callee_id, group_id, media_type, status,\n" +
                "       started_at, answered_at, ended_at, turn_session_id, pjsip_session_id, media_status, media_error\n" +
                "FROM call_records\n" +
                "WHERE id = ?\n", (rs, rowNum) -> mapCall(rs), callId);
        if (calls.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "call not found");
        }
        return calls.get(0);
    }

    private CallRecord transition(String callId, String nextStatus, String actorId) {
        val current = get(callId);
        ensureActorAllowed(current, nextStatus, actorId);
        ensureTransitionAllowed(current.status(), nextStatus);
        val answeredAt = "answered".equals(nextStatus);
        val endedAt = Arrays.asList("rejected", "ended").contains(nextStatus);
        int updated = jdbcTemplate.update("UPDATE call_records\n" +
                "SET status = ?,\n" +
                "    answered_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE answered_at END,\n" +
                "    ended_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE ended_at END\n" +
                "WHERE id = ?\n", nextStatus, answeredAt, endedAt, callId);
        if (updated == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "call not found");
        }
        CallRecord record = get(callId);
        if ("answered".equals(nextStatus)) {
            applyGatewayResult(callId, pjsipGatewayClient.answer(record, actorId));
            record = get(callId);
        }
        if ("rejected".equals(nextStatus) || "ended".equals(nextStatus)) {
            applyGatewayResult(callId, pjsipGatewayClient.hangup(record, actorId));
            record = get(callId);
        }
        realtimeNotifier.notifyCallUpdated(record);
        return record;
    }

    private void applyGatewayResult(String callId, PjsipGatewayClient.MediaGatewayResult result) {
        jdbcTemplate.update("UPDATE call_records\n" +
                "SET pjsip_session_id = COALESCE(?, pjsip_session_id),\n" +
                "    media_status = ?,\n" +
                "    media_error = ?\n" +
                "WHERE id = ?\n", result.sessionId(), result.mediaStatus(), result.error(), callId);
    }

    private void ensureTransitionAllowed(String currentStatus, String nextStatus) {
        boolean allowed;
        if ("ringing".equals(currentStatus)) {
            allowed = Arrays.asList("answered", "rejected", "ended").contains(nextStatus);
        } else if ("answered".equals(currentStatus)) {
            allowed = "ended".equals(nextStatus);
        } else {
            allowed = false;
        }
        if (!allowed) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid call transition: " + currentStatus + " -> " + nextStatus);
        }
    }

    private void ensureActorAllowed(CallRecord current, String nextStatus, String actorId) {
        if ("answered".equals(nextStatus) || "rejected".equals(nextStatus)) {
            if (!actorId.equals(current.calleeId())) {
                throw new ResponseStatusException(FORBIDDEN, "only callee can " + nextStatus);
            }
            return;
        }
        if (!actorId.equals(current.callerId()) && !actorId.equals(current.calleeId())) {
            throw new ResponseStatusException(FORBIDDEN, "only call participants can change call state");
        }
    }

    private void ensureConversation(String conversationId, String type, String targetId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversations WHERE id = ?", Integer.class, conversationId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id)\n" +
                "VALUES (?, ?, ?)\n", conversationId, type, targetId);
    }

    private void ensureConversationMember(String conversationId, String userId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversation_members\n" +
                "WHERE conversation_id = ? AND user_id = ?\n", Integer.class, conversationId, userId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id)\n" +
                "VALUES (?, ?)\n", conversationId, userId);
    }

    private String normalizeMediaType(String value) {
        val mediaType = value == null || value.trim().isEmpty() ? "audio" : value.toLowerCase();
        if (!Arrays.asList("audio", "video").contains(mediaType)) {
            throw new ResponseStatusException(BAD_REQUEST, "mediaType must be audio or video");
        }
        return mediaType;
    }

    private String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, name + " is required");
        }
        return value;
    }

    private String sipUri(String userId, String domain) {
        return "sip:" + userId + "@" + domain;
    }

    private String sipHost(String registrar) {
        if (registrar == null || registrar.trim().isEmpty()) {
            return properties.getRealtime().getSipDomain();
        }
        val trimmed = registrar.trim();
        val withoutScheme = trimmed.startsWith("sip:") ? trimmed.substring(4) : trimmed;
        val withoutUser = withoutScheme.contains("@")
                ? withoutScheme.substring(withoutScheme.indexOf('@') + 1)
                : withoutScheme;
        val hostPort = withoutUser.contains(";") ? withoutUser.substring(0, withoutUser.indexOf(';')) : withoutUser;
        return hostPort.trim().isEmpty() ? properties.getRealtime().getSipDomain() : hostPort;
    }

    private ReadinessCheck check(String name, String value, String missingMessage) {
        val ready = value != null && !value.trim().isEmpty();
        return new ReadinessCheck(name, ready, ready ? "检查通过" : missingMessage);
    }

    private ConnectivityCheck socketProbe(String name, String url, int defaultPort, int timeoutMs) {
        val started = System.nanoTime();
        if (url == null || url.trim().isEmpty()) {
            return new ConnectivityCheck(name, false, null, 0, "not configured", elapsedMs(started));
        }
        try {
            val target = parseTarget(url, defaultPort);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(target.host(), target.port()), (int) Duration.ofMillis(timeoutMs).toMillis());
                return new ConnectivityCheck(name, true, target.host(), target.port(), "reachable", elapsedMs(started));
            }
        } catch (Exception ex) {
            val target = safeParseTarget(url, defaultPort);
            return new ConnectivityCheck(name, false, target.host(), target.port(), ex.getClass().getSimpleName(), elapsedMs(started));
        }
    }

    private long elapsedMs(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private ProbeTarget safeParseTarget(String url, int defaultPort) {
        try {
            return parseTarget(url, defaultPort);
        } catch (Exception ignored) {
            return new ProbeTarget(url, defaultPort);
        }
    }

    private ProbeTarget parseTarget(String url, int defaultPort) {
        if (url.startsWith("turn:") || url.startsWith("turns:")) {
            String value = url.substring(url.indexOf(':') + 1).replaceFirst("^//", "");
            val end = value.indexOf('?');
            if (end >= 0) {
                value = value.substring(0, end);
            }
            val parts = value.split(":", 2);
            return new ProbeTarget(parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : defaultPort);
        }
        val uri = URI.create(url);
        val port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : defaultPort);
        return new ProbeTarget(uri.getHost(), port);
    }

    private CallRecord mapCall(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CallRecord(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("caller_id"),
                rs.getString("callee_id"),
                rs.getString("group_id"),
                rs.getString("media_type"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("answered_at")),
                toInstant(rs.getTimestamp("ended_at")),
                rs.getString("turn_session_id"),
                rs.getString("pjsip_session_id"),
                rs.getString("media_status"),
                rs.getString("media_error")
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class InitiateCallRequest {
        private String callerId;
        private String calleeId;
        private String conversationId;
        private String mediaType;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class TransitionCallRequest {
        private String actorId;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class CallConfig {
        private String turnUrl;
        private String turnUsername;
        private String turnPassword;
        private String pjsipSignalUrl;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class MediaConfig {
        private String sipDomain;
        private String sipRegistrar;
        private String sipRealm;
        private String sipUsername;
        private String sipPassword;
        private String selfSipUri;
        private String calleeSipUri;
        private String turnUrl;
        private String turnUsername;
        private String turnPassword;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class ReadinessCheck {
        private String name;
        private boolean ready;
        private String message;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class ConnectivityCheck {
        private String name;
        private boolean reachable;
        private String host;
        private int port;
        private String message;
        private long durationMs;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    private static class ProbeTarget {
        private String host;
        private int port;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class CallReadiness {
        private boolean ready;
        private List<String> supportedMediaTypes;
        private List<ReadinessCheck> checks;
        private List<String> blockers;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class CallConnectivity {
        private boolean reachable;
        private List<ConnectivityCheck> checks;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class CallRecord {
        private String id;
        private String conversationId;
        private String callerId;
        private String calleeId;
        private String groupId;
        private String mediaType;
        private String status;
        private Instant startedAt;
        private Instant answeredAt;
        private Instant endedAt;
        private String turnSessionId;
        private String pjsipSessionId;
        private String mediaStatus;
        private String mediaError;
    }
}
