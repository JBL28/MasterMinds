package com.masterminds.player;

import com.masterminds.character.CharacterRole;

public record Player(
        String playerToken,
        String name,
        boolean host,
        CharacterRole role,
        boolean alive
) {

    public Player(String playerToken, String name, boolean host) {
        this(playerToken, name, host, null, true);
    }

    public Player withRole(CharacterRole role) {
        return new Player(playerToken, name, host, role, alive);
    }

    public Player kill() {
        return new Player(playerToken, name, host, role, false);
    }
}
