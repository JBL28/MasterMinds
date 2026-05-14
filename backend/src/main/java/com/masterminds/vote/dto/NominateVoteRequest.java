package com.masterminds.vote.dto;

public record NominateVoteRequest(
        String playerToken,
        String targetToken
) {
}
