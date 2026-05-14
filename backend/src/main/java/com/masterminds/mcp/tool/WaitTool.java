package com.masterminds.mcp.tool;

import com.masterminds.game.WaitService;
import com.masterminds.player.Player;
import com.masterminds.room.Room;
import com.masterminds.room.RoomService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WaitTool {

    private final WaitService waitService;
    private final RoomService roomService;

    public WaitTool(WaitService waitService, RoomService roomService) {
        this.waitService = waitService;
        this.roomService = roomService;
    }

    @McpTool(
        name = "wait",
        description = "Wait until the next game phase starts, or return DEAD for a dead player."
    )
    public Map<String, Object> wait(
            @McpToolParam(description = "Room code", required = true) String roomCode,
            @McpToolParam(description = "Player token", required = true) String playerToken) {
        Room room = roomService.getRoom(roomCode);
        Player player = room.findPlayer(playerToken).orElse(null);
        if (player != null && !player.alive()) {
            return Map.of(
                    "phase", "DEAD",
                    "message", "You are dead. Cemetery chat is available."
            );
        }
        return waitService.waitForNextPhase(roomCode, playerToken);
    }
}
