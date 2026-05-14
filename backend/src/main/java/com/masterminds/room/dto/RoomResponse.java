package com.masterminds.room.dto;

import com.masterminds.room.RoomStatus;

import java.time.Instant;
import java.util.List;

public record RoomResponse(
        String roomCode,
        RoomStatus status,
        Instant createdAt,
        List<PlayerResponse> players
) {
}
