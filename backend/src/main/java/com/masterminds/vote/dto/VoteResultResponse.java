package com.masterminds.vote.dto;

public record VoteResultResponse(
        boolean resolved,
        String targetToken,
        boolean executed
) {
}
