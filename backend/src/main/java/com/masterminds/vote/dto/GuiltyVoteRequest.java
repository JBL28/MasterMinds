package com.masterminds.vote.dto;

public record GuiltyVoteRequest(
        String playerToken,
        boolean guilty
) {
}
