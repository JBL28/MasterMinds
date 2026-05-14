package com.masterminds.mcp.tool;

import com.masterminds.night.NightActionResult;
import com.masterminds.night.NightService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class NightTool {

    private final NightService nightService;

    public NightTool(NightService nightService) {
        this.nightService = nightService;
    }

    @McpTool(name = "nightKill", description = "Submit the mafia night kill target.")
    public Map<String, Object> nightKill(
            @McpToolParam(description = "Room code", required = true) String roomCode,
            @McpToolParam(description = "Player token", required = true) String playerToken,
            @McpToolParam(description = "Target player token", required = true) String targetToken) {
        return toMap(nightService.nightKill(roomCode, playerToken, targetToken));
    }

    @McpTool(name = "nightInvestigate", description = "Investigate whether the target is mafia.")
    public Map<String, Object> nightInvestigate(
            @McpToolParam(description = "Room code", required = true) String roomCode,
            @McpToolParam(description = "Player token", required = true) String playerToken,
            @McpToolParam(description = "Target player token", required = true) String targetToken) {
        return toMap(nightService.nightInvestigate(roomCode, playerToken, targetToken));
    }

    @McpTool(name = "nightProtect", description = "Protect a target from the mafia kill.")
    public Map<String, Object> nightProtect(
            @McpToolParam(description = "Room code", required = true) String roomCode,
            @McpToolParam(description = "Player token", required = true) String playerToken,
            @McpToolParam(description = "Target player token", required = true) String targetToken) {
        return toMap(nightService.nightProtect(roomCode, playerToken, targetToken));
    }

    private Map<String, Object> toMap(NightActionResult result) {
        Map<String, Object> response = new HashMap<>();
        response.put("resolved", result.resolved());
        response.put("targetToken", result.targetToken() == null ? "" : result.targetToken());
        response.put("killedToken", result.killedToken() == null ? "" : result.killedToken());
        response.put("protectedTarget", result.protectedTarget());
        if (result.mafia() != null) {
            response.put("mafia", result.mafia());
        }
        return response;
    }
}
