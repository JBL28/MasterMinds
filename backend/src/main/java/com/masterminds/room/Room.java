package com.masterminds.room;

import com.masterminds.character.CharacterRole;
import com.masterminds.game.GamePhase;
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
    private GamePhase gamePhase;
    private int dayTurn;
    private Instant phaseStartedAt;
    private Instant phaseEndsAt;

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

    public synchronized GamePhase getGamePhase() {
        return gamePhase;
    }

    public synchronized int getDayTurn() {
        return dayTurn;
    }

    public synchronized Instant getPhaseStartedAt() {
        return phaseStartedAt;
    }

    public synchronized Instant getPhaseEndsAt() {
        return phaseEndsAt;
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

    public synchronized void startGame(Instant now) {
        status = RoomStatus.IN_GAME;
        setPhase(GamePhase.DAY_CHAT, 1, now, 30);
    }

    public synchronized void advancePhase(Instant now) {
        if (status != RoomStatus.IN_GAME || gamePhase == null) {
            throw new IllegalStateException("Game has not started.");
        }

        switch (gamePhase) {
            case DAY_CHAT -> {
                if (dayTurn < 3) {
                    setPhase(GamePhase.DAY_CHAT, dayTurn + 1, now, 30);
                } else {
                    setPhase(GamePhase.VOTE_NOMINATE, dayTurn, now, 30);
                }
            }
            case VOTE_NOMINATE -> setPhase(GamePhase.FINAL_SPEECH, dayTurn, now, 30);
            case FINAL_SPEECH -> setPhase(GamePhase.VOTE_GUILTY, dayTurn, now, 30);
            case VOTE_GUILTY -> setPhase(GamePhase.NIGHT, dayTurn, now, 60);
            case NIGHT -> setPhase(GamePhase.DAY_CHAT, 1, now, 30);
            case GAME_OVER -> {
            }
        }
    }

    private void setPhase(GamePhase gamePhase, int dayTurn, Instant now, long durationSeconds) {
        this.gamePhase = gamePhase;
        this.dayTurn = dayTurn;
        this.phaseStartedAt = now;
        this.phaseEndsAt = now.plusSeconds(durationSeconds);
    }
}
