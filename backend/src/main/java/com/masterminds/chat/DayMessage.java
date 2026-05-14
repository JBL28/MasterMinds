package com.masterminds.chat;

import java.time.Instant;

public record DayMessage(
        String playerToken,
        String playerName,
        int dayTurn,
        String message,
        Instant submittedAt
) {
}
