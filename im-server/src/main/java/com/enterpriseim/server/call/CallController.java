package com.enterpriseim.server.call;

import lombok.val;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.auth.UserAuthService;
import com.enterpriseim.server.config.ImProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/calls")
public class CallController {
    private final CallService callService;
    private final UserAuthService userAuthService;
    private final ImProperties properties;

    public CallController(CallService callService, UserAuthService userAuthService, ImProperties properties) {
        this.callService = callService;
        this.userAuthService = userAuthService;
        this.properties = properties;
    }

    @GetMapping("/config")
    public ApiResponse<CallService.CallConfig> config(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        userAuthService.requireUser(authorization);
        return ApiResponse.ok(callService.config());
    }

    @GetMapping("/media-config")
    public ApiResponse<CallService.MediaConfig> mediaConfig(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String userId,
            @RequestParam(required = false) String calleeId,
            @RequestParam(defaultValue = "desktop") String platform
    ) {
        userAuthService.requireSameUser(userAuthService.requireUser(authorization), userId);
        return ApiResponse.ok(callService.mediaConfig(userId, calleeId, platform));
    }

    @GetMapping("/readiness")
    public ApiResponse<CallService.CallReadiness> readiness() {
        return ApiResponse.ok(callService.readiness());
    }

    @PostMapping
    public ApiResponse<CallService.CallRecord> initiate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CallService.InitiateCallRequest request
    ) {
        userAuthService.requireSameUser(userAuthService.requireUser(authorization), request.callerId());
        return ApiResponse.ok(callService.initiate(request));
    }

    @PostMapping("/{callId}/answer")
    public ApiResponse<CallService.CallRecord> answer(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String callId,
            @RequestBody CallService.TransitionCallRequest request
    ) {
        userAuthService.requireSameUser(userAuthService.requireUser(authorization), request.actorId());
        return ApiResponse.ok(callService.answer(callId, request));
    }

    @PostMapping("/{callId}/reject")
    public ApiResponse<CallService.CallRecord> reject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String callId,
            @RequestBody CallService.TransitionCallRequest request
    ) {
        userAuthService.requireSameUser(userAuthService.requireUser(authorization), request.actorId());
        return ApiResponse.ok(callService.reject(callId, request));
    }

    @PostMapping("/{callId}/demo-answer")
    public ApiResponse<CallService.CallRecord> demoAnswer(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String callId
    ) {
        requireDemoCallEndpointsEnabled();
        val actorId = userAuthService.requireUser(authorization);
        val current = callService.get(callId);
        userAuthService.requireSameUser(actorId, current.callerId());
        return ApiResponse.ok(callService.answer(callId, new CallService.TransitionCallRequest(current.calleeId())));
    }

    @PostMapping("/{callId}/demo-reject")
    public ApiResponse<CallService.CallRecord> demoReject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String callId
    ) {
        requireDemoCallEndpointsEnabled();
        val actorId = userAuthService.requireUser(authorization);
        val current = callService.get(callId);
        userAuthService.requireSameUser(actorId, current.callerId());
        return ApiResponse.ok(callService.reject(callId, new CallService.TransitionCallRequest(current.calleeId())));
    }

    @PostMapping("/{callId}/hangup")
    public ApiResponse<CallService.CallRecord> hangup(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String callId,
            @RequestBody CallService.TransitionCallRequest request
    ) {
        userAuthService.requireSameUser(userAuthService.requireUser(authorization), request.actorId());
        return ApiResponse.ok(callService.hangup(callId, request));
    }

    @GetMapping
    public ApiResponse<List<CallService.CallRecord>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String userId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        userAuthService.requireSameUser(userAuthService.requireUser(authorization), userId);
        return ApiResponse.ok(callService.listByUser(userId, limit));
    }

    private void requireDemoCallEndpointsEnabled() {
        if (!properties.getAuth().isDemoCallEndpointsEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "演示通话端点已禁用");
        }
    }
}
