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

    @McpTool(
        name = "hypnotizeDayMessage",
        description = "As the hypnotist, submit your own DAY_CHAT message and replace one living target player's message for this turn."
    )
    public Map<String, Object> hypnotizeDayMessage(
            @McpToolParam(description = "Room code", required = true) String roomCode,
            @McpToolParam(description = "Hypnotist player token", required = true) String playerToken,
            @McpToolParam(description = "Target player token", required = true) String targetToken,
            @McpToolParam(description = "Hypnotist's own day discussion message", required = true) String message,
            @McpToolParam(description = "Replacement message that will be revealed for the target", required = true) String replacementMessage) {
        DayMessageResult result = chatService.hypnotize(roomCode, playerToken, targetToken, message, replacementMessage);
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
