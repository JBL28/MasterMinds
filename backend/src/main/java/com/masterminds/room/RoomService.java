package com.masterminds.room;

import com.masterminds.character.CharacterAssignmentService;
import com.masterminds.game.GamePhase;
import com.masterminds.game.WaitService;
import com.masterminds.player.Player;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {

    private static final int ROOM_CODE_LENGTH = 6;
    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CharacterAssignmentService characterAssignmentService;
    private final WaitService waitService;

    public RoomService(CharacterAssignmentService characterAssignmentService, WaitService waitService) {
        this.characterAssignmentService = characterAssignmentService;
        this.waitService = waitService;
    }

    public Room createRoom() {
        String roomCode = generateUniqueRoomCode();
        Room room = new Room(roomCode, Instant.now());
        rooms.put(roomCode, room);
        return room;
    }

    public Player joinRoom(String roomCode, String playerName) {
        Room room = getRoom(roomCode);
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw new RoomRuleException("Cannot join a room after the game starts.");
        }

        String normalizedName = Optional.ofNullable(playerName)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .orElseThrow(() -> new RoomRuleException("Player name is required."));

        return room.addPlayer(UUID.randomUUID().toString(), normalizedName);
    }

    public Room getRoom(String roomCode) {
        Room room = rooms.get(normalizeRoomCode(roomCode));
        if (room == null) {
            throw new RoomNotFoundException(roomCode);
        }
        return room;
    }

    public Room startRoom(String roomCode, String playerToken) {
        Room room = getRoom(roomCode);
        if (room.getStatus() != RoomStatus.LOBBY) {
            return room;
        }
        if (!room.hasPlayer(playerToken)) {
            throw new RoomRuleException("Player token does not belong to this room.");
        }
        if (!room.isHost(playerToken)) {
            throw new RoomRuleException("Only the host can start the game.");
        }
        try {
            room.assignRoles(characterAssignmentService.assignRoles(room.getPlayers().size()));
        } catch (IllegalArgumentException exception) {
            throw new RoomRuleException(exception.getMessage());
        }
        room.startGame(Instant.now());
        resolveWaitersWithAssignments(room);
        return room;
    }

    public Room advancePhase(String roomCode, String playerToken) {
        Room room = getRoom(roomCode);
        if (room.getStatus() != RoomStatus.IN_GAME) {
            throw new RoomRuleException("Game has not started.");
        }
        if (!room.isHost(playerToken)) {
            throw new RoomRuleException("Only the host can advance the phase.");
        }
        try {
            room.advancePhase(Instant.now());
        } catch (IllegalStateException exception) {
            throw new RoomRuleException(exception.getMessage());
        }
        resolveWaitersWithPhase(room);
        return room;
    }

    public Room advancePhaseFromSystem(String roomCode, Map<String, Object> payload) {
        Room room = getRoom(roomCode);
        if (room.getStatus() != RoomStatus.IN_GAME) {
            throw new RoomRuleException("Game has not started.");
        }
        try {
            room.advancePhase(Instant.now());
        } catch (IllegalStateException exception) {
            throw new RoomRuleException(exception.getMessage());
        }
        resolveWaitersWithPhase(room, payload);
        return room;
    }

    public Room finishNightFromSystem(String roomCode, Map<String, Object> payload) {
        Room room = getRoom(roomCode);
        if (room.getStatus() != RoomStatus.IN_GAME) {
            throw new RoomRuleException("Game has not started.");
        }
        room.finishNight(Instant.now());
        resolveWaitersWithPhase(room, payload);
        return room;
    }

    public void resolveWaitersForCurrentPhase(Room room, Map<String, Object> payload) {
        resolveWaitersWithPhase(room, payload);
    }

    public Player getPlayerAssignment(String roomCode, String playerToken) {
        Room room = getRoom(roomCode);
        return room.findPlayer(playerToken)
                .orElseThrow(() -> new RoomRuleException("Player token does not belong to this room."));
    }

    private String generateUniqueRoomCode() {
        String roomCode;
        do {
            roomCode = generateRoomCode();
        } while (rooms.containsKey(roomCode));
        return roomCode;
    }

    private String generateRoomCode() {
        StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            code.append(ROOM_CODE_ALPHABET.charAt(random.nextInt(ROOM_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private String normalizeRoomCode(String roomCode) {
        return Optional.ofNullable(roomCode)
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .orElse("");
    }

    private void resolveWaitersWithAssignments(Room room) {
        for (Player player : room.getPlayers()) {
            waitService.resolve(room.getCode(), player.playerToken(), phasePayload(room, Map.of(
                    "role", player.role().name(),
                    "displayName", player.role().getDisplayName(),
                    "description", player.role().getDescription(),
                    "lawyerClientToken", room.getLawyerClientToken() == null ? "" : room.getLawyerClientToken()
            )));
        }
    }

    private void resolveWaitersWithPhase(Room room) {
        resolveWaitersWithPhase(room, Map.of());
    }

    private void resolveWaitersWithPhase(Room room, Map<String, Object> extra) {
        for (Player player : room.getPlayers()) {
            if (!player.alive() && room.getGamePhase() != GamePhase.GAME_OVER) {
                waitService.resolve(room.getCode(), player.playerToken(), Map.of(
                        "phase", "DEAD",
                        "message", "You are dead. Cemetery chat is available."
                ));
                continue;
            }
            waitService.resolve(room.getCode(), player.playerToken(), phasePayload(room, extra));
        }
    }

    private Map<String, Object> phasePayload(Room room, Map<String, Object> extra) {
        GamePhase gamePhase = room.getGamePhase();
        Map<String, Object> payload = new HashMap<>();
        payload.put("phase", gamePhase == null ? room.getStatus().name() : gamePhase.name());
        payload.put("dayTurn", room.getDayTurn());
        payload.put("phaseEndsAt", room.getPhaseEndsAt());
        payload.put("result", room.getResult() == null ? "" : room.getResult());
        payload.put("lawyerWin", room.isLawyerWin());
        payload.putAll(extra);
        return payload;
    }
}
