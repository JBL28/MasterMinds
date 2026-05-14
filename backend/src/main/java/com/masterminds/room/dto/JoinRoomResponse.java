package com.masterminds.room.dto;

public record JoinRoomResponse(
        String roomCode,
        String playerToken,
        String name,
        boolean host
) {
}
