package com.masterminds.night.dto;

public record NightActionResponse(
        boolean resolved,
        String targetToken,
        String killedToken,
        boolean protectedTarget,
        Boolean mafia
) {
}
