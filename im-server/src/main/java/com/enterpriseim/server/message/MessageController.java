package com.enterpriseim.server.message;

import com.enterpriseim.server.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageService.MessageItem>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(messageService.listMessages(conversationId, limit));
    }
}
