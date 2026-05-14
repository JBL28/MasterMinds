package com.masterminds.room;

import com.masterminds.player.Player;
import com.masterminds.room.dto.JoinRoomResponse;
import com.masterminds.room.dto.PlayerResponse;
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
                room.getCreatedAt(),
                room.getPlayers().stream()
                        .map(RoomMapper::toPlayerResponse)
                        .toList()
        );
    }

    private static PlayerResponse toPlayerResponse(Player player) {
        return new PlayerResponse(player.playerToken(), player.name(), player.host());
    }
}
