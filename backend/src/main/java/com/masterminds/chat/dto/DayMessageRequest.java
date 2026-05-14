package com.masterminds.chat.dto;

public record DayMessageRequest(
        String playerToken,
        String message
) {
}
