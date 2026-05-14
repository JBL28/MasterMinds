package com.masterminds.chat;

import java.time.Instant;

public record CemeteryMessage(
        String playerToken,
        String playerName,
        String message,
        Instant submittedAt
) {
}
