package com.masterminds.vote;

public record VotePhaseResult(
        boolean resolved,
        String targetToken,
        boolean executed
) {
}
