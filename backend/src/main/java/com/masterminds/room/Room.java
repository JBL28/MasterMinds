package com.masterminds.room;

import com.masterminds.character.CharacterRole;
import com.masterminds.player.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Room {

    private final String code;
    private final Instant createdAt;
    private final List<Player> players = new ArrayList<>();
    private RoomStatus status = RoomStatus.LOBBY;

    public Room(String code, Instant createdAt) {
        this.code = code;
        this.createdAt = createdAt;
    }

    public String getCode() {
        return code;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized RoomStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(RoomStatus status) {
        this.status = status;
    }

    public synchronized List<Player> getPlayers() {
        return Collections.unmodifiableList(new ArrayList<>(players));
    }

    public synchronized Player addPlayer(String playerToken, String name) {
        boolean host = players.isEmpty();
        Player player = new Player(playerToken, name, host);
        players.add(player);
        return player;
    }

    public synchronized boolean hasPlayer(String playerToken) {
        return players.stream().anyMatch(player -> player.playerToken().equals(playerToken));
    }

    public synchronized boolean isHost(String playerToken) {
        return players.stream()
                .anyMatch(player -> player.playerToken().equals(playerToken) && player.host());
    }

    public synchronized Optional<Player> findPlayer(String playerToken) {
        return players.stream()
                .filter(player -> player.playerToken().equals(playerToken))
                .findFirst();
    }

    public synchronized void assignRoles(List<CharacterRole> roles) {
        if (roles.size() != players.size()) {
            throw new IllegalArgumentException("Role count must match player count.");
        }
        for (int i = 0; i < players.size(); i++) {
            players.set(i, players.get(i).withRole(roles.get(i)));
        }
    }
}
