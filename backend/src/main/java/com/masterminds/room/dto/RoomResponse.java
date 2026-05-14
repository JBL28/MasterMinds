package com.masterminds.room.dto;

import com.masterminds.game.GamePhase;
import com.masterminds.room.RoomStatus;

import java.time.Instant;
import java.util.List;

public record RoomResponse(
        String roomCode,
        RoomStatus status,
        GamePhase gamePhase,
        int dayTurn,
        String nominatedPlayerToken,
        Instant phaseStartedAt,
        Instant phaseEndsAt,
        Instant createdAt,
        List<PlayerResponse> players
) {
}
