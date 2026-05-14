package com.masterminds.room.dto;

public record PlayerResponse(
        String playerToken,
        String name,
        boolean host,
        boolean alive
) {
}
