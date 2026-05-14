package com.masterminds.night;

public record NightActionResult(
        boolean resolved,
        String targetToken,
        String killedToken,
        boolean protectedTarget,
        Boolean mafia
) {
}
