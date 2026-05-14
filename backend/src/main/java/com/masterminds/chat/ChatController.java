package com.masterminds.chat;

import com.masterminds.chat.dto.DayMessageRequest;
import com.masterminds.chat.dto.DayMessageSubmissionResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{code}")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/day-messages")
    public DayMessageSubmissionResponse sendDayMessage(
            @PathVariable String code,
            @RequestBody DayMessageRequest request
    ) {
        return ChatMapper.toSubmissionResponse(
                chatService.sendDayMessage(code, request.playerToken(), request.message())
        );
    }
}
