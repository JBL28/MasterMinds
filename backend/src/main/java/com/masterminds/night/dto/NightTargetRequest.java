package com.masterminds.night.dto;

public record NightTargetRequest(
        String playerToken,
        String targetToken
) {
}
