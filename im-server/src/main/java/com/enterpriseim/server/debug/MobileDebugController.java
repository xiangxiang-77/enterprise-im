package com.enterpriseim.server.debug;

import com.enterpriseim.server.api.ApiResponse;
import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug/mobile")
public class MobileDebugController {
    private static final Logger log = LoggerFactory.getLogger(MobileDebugController.class);

    @PostMapping("/logs")
    public ApiResponse<MobileLogResponse> receive(@Valid @RequestBody MobileLogRequest request) {
        String text = request.getText() == null ? "" : request.getText();
        if (text.length() > 24000) {
            text = text.substring(text.length() - 24000);
        }
        log.warn("MOBILE_DIAG user={} event={} at={} text=\n{}",
                request.getUserId(),
                request.getEvent(),
                Instant.now(),
                text);
        return ApiResponse.ok(new MobileLogResponse("accepted", text.length()));
    }

    @Data
    public static class MobileLogRequest {
        @NotBlank
        private String userId;

        @NotBlank
        private String event;

        @Size(max = 30000)
        private String text;
    }

    @Data
    public static class MobileLogResponse {
        private final String status;
        private final int bytes;
    }
}
