package com.masterminds.chat.dto;

public record HypnotizeRequest(
        String playerToken,
        String targetToken,
        String message,
        String replacementMessage
) {
}
