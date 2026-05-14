package com.masterminds.room;

import com.masterminds.player.Player;
import com.masterminds.room.dto.JoinRoomResponse;
import com.masterminds.room.dto.PlayerResponse;
import com.masterminds.room.dto.RoleAssignmentResponse;
import com.masterminds.room.dto.RoomResponse;

public final class RoomMapper {

    private RoomMapper() {
    }

    public static JoinRoomResponse toJoinRoomResponse(Room room, Player player) {
        return new JoinRoomResponse(room.getCode(), player.playerToken(), player.name(), player.host());
    }

    public static RoomResponse toRoomResponse(Room room) {
        return new RoomResponse(
                room.getCode(),
                room.getStatus(),
                room.getGamePhase(),
                room.getDayTurn(),
                room.getNominatedPlayerToken(),
                room.getPhaseStartedAt(),
                room.getPhaseEndsAt(),
                room.getCreatedAt(),
                room.getPlayers().stream()
                        .map(RoomMapper::toPlayerResponse)
                        .toList()
        );
    }

    private static PlayerResponse toPlayerResponse(Player player) {
        return new PlayerResponse(player.playerToken(), player.name(), player.host(), player.alive());
    }

    public static RoleAssignmentResponse toRoleAssignmentResponse(Room room, Player player) {
        if (player.role() == null) {
            throw new RoomRuleException("Character has not been assigned yet.");
        }
        return new RoleAssignmentResponse(
                room.getCode(),
                player.playerToken(),
                player.role(),
                player.role().getDisplayName(),
                player.role().getDescription()
        );
    }
}
