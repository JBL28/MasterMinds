package com.masterminds.mcp.tool;

import com.masterminds.chat.ChatService;
import com.masterminds.chat.DayMessage;
import com.masterminds.chat.DayMessageResult;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DayMessageTool {

    private final ChatService chatService;

    public DayMessageTool(ChatService chatService) {
        this.chatService = chatService;
    }

    @McpTool(
        name = "sendDayMessage",
        description = "Submit this player's day discussion message during DAY_CHAT. Messages are revealed after every player submits."
    )
    public Map<String, Object> sendDayMessage(
            @McpToolParam(description = "Room code", required = true) String roomCode,
            @McpToolParam(description = "Player token", required = true) String playerToken,
            @McpToolParam(description = "Day discussion message", required = true) String message) {
        DayMessageResult result = chatService.sendDayMessage(roomCode, playerToken, message);
        return Map.of(
                "revealed", result.revealed(),
                "messages", result.messages().stream()
                        .map(DayMessageTool::toMap)
                        .toList()
        );
    }

    private static Map<String, Object> toMap(DayMessage message) {
        return Map.of(
                "playerToken", message.playerToken(),
                "playerName", message.playerName(),
                "dayTurn", message.dayTurn(),
                "message", message.message(),
                "submittedAt", message.submittedAt().toString()
        );
    }
}
