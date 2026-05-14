package com.masterminds.mcp.tool;

import com.masterminds.game.WaitService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WaitTool {

    private final WaitService waitService;

    public WaitTool(WaitService waitService) {
        this.waitService = waitService;
    }

    @McpTool(
        name = "wait",
        description = "다음 페이즈/턴이 시작될 때까지 대기합니다. 응답으로 현재 페이즈/턴 정보가 반환됩니다."
    )
    public Map<String, Object> wait(
            @McpToolParam(description = "방 코드 (6자리)", required = true) String roomCode,
            @McpToolParam(description = "플레이어 토큰", required = true) String playerToken) {
        return waitService.waitForNextPhase(roomCode, playerToken);
    }
}
