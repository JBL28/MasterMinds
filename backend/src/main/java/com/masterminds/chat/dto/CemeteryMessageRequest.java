package com.masterminds.chat.dto;

public record CemeteryMessageRequest(
        String playerToken,
        String message
) {
}
