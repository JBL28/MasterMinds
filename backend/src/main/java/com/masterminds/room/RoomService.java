package com.masterminds.room;

import com.masterminds.player.Player;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
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
        room.setStatus(RoomStatus.IN_GAME);
        return room;
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
}
