package com.masterminds.chat.dto;

import java.time.Instant;

public record DayMessageResponse(
        String playerToken,
        String playerName,
        int dayTurn,
        String message,
        Instant submittedAt
) {
}
