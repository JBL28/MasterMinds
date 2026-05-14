package com.masterminds.chat;

import com.masterminds.chat.dto.CemeteryMessageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class CemeterySocketController {

    private final CemeteryMessageService cemeteryMessageService;

    public CemeterySocketController(CemeteryMessageService cemeteryMessageService) {
        this.cemeteryMessageService = cemeteryMessageService;
    }

    @MessageMapping("/rooms/{code}/cemetery")
    public void sendCemeteryMessage(
            @DestinationVariable String code,
            CemeteryMessageRequest request
    ) {
        cemeteryMessageService.sendCemeteryMessage(code, request.playerToken(), request.message());
    }
}
