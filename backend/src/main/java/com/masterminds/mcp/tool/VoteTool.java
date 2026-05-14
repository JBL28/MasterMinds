package com.masterminds.mcp.tool;

import com.masterminds.vote.VotePhaseResult;
import com.masterminds.vote.VoteService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VoteTool {

    private final VoteService voteService;

    public VoteTool(VoteService voteService) {
        this.voteService = voteService;
    }

    @McpTool(name = "getLastWords", description = "Return the nominated player's final speech prompt.")
    public Map<String, Object> getLastWords(
            @McpToolParam(description = "Room code", required = true) String roomCode,
            @McpToolParam(description = "Player token", required = true) String playerToken) {
        return Map.of("prompt", voteService.getLastWords(roomCode, playerToken));
    }

    @McpTool(name = "voteGuilty", description = "Submit a guilty or not-guilty vote for the nominated player.")
    public Map<String, Object> voteGuilty(
            @McpToolParam(description = "Room code", required = true) String roomCode,
            @McpToolParam(description = "Player token", required = true) String playerToken,
            @McpToolParam(description = "Whether this player votes guilty", required = true) boolean guilty) {
        VotePhaseResult result = voteService.voteGuilty(roomCode, playerToken, guilty);
        return Map.of(
                "resolved", result.resolved(),
                "targetToken", result.targetToken() == null ? "" : result.targetToken(),
                "executed", result.executed()
        );
    }
}
