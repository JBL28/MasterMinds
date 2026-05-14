package com.masterminds.player;

public record Player(
        String playerToken,
        String name,
        boolean host
) {
}
