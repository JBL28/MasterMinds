package com.masterminds.vote;

import com.masterminds.game.GamePhase;
import com.masterminds.player.Player;
import com.masterminds.room.Room;
import com.masterminds.room.RoomRuleException;
import com.masterminds.room.RoomService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VoteService {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> nominateVotes =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> guiltyVotes =
            new ConcurrentHashMap<>();

    private final RoomService roomService;

    public VoteService(RoomService roomService) {
        this.roomService = roomService;
    }

    public VotePhaseResult nominate(String roomCode, String playerToken, String targetToken) {
        Room room = roomService.getRoom(roomCode);
        validatePhase(room, GamePhase.VOTE_NOMINATE);
        validateAlivePlayer(room, playerToken);
        validateAlivePlayer(room, targetToken);

        String key = voteKey(room, "nominate");
        ConcurrentHashMap<String, String> votes = nominateVotes.computeIfAbsent(
                key,
                ignored -> new ConcurrentHashMap<>()
        );
        if (votes.putIfAbsent(playerToken, targetToken) != null) {
            throw new RoomRuleException("Player already submitted a nomination vote.");
        }

        if (votes.size() < room.getAlivePlayers().size()) {
            return new VotePhaseResult(false, null, false);
        }

        String nominatedToken = resolveMostVotedTarget(votes);
        nominateVotes.remove(key);
        room.nominate(nominatedToken, Instant.now());
        roomService.resolveWaitersForCurrentPhase(room, Map.of("targetToken", nominatedToken));
        return new VotePhaseResult(true, nominatedToken, false);
    }

    public String getLastWords(String roomCode, String playerToken) {
        Room room = roomService.getRoom(roomCode);
        validatePhase(room, GamePhase.FINAL_SPEECH);
        if (!playerToken.equals(room.getNominatedPlayerToken())) {
            throw new RoomRuleException("Only the nominated player can provide final words.");
        }
        Player player = room.findPlayer(playerToken)
                .orElseThrow(() -> new RoomRuleException("Player token does not belong to this room."));
        return player.name() + ", give your final defense before the guilty vote.";
    }

    public Room beginGuiltyVote(String roomCode, String playerToken) {
        Room room = roomService.getRoom(roomCode);
        if (!playerToken.equals(room.getNominatedPlayerToken())) {
            throw new RoomRuleException("Only the nominated player can end final speech.");
        }
        room.beginGuiltyVote(Instant.now());
        roomService.resolveWaitersForCurrentPhase(room, Map.of("targetToken", room.getNominatedPlayerToken()));
        return room;
    }

    public VotePhaseResult voteGuilty(String roomCode, String playerToken, boolean guilty) {
        Room room = roomService.getRoom(roomCode);
        validatePhase(room, GamePhase.VOTE_GUILTY);
        validateAlivePlayer(room, playerToken);

        String key = voteKey(room, "guilty");
        ConcurrentHashMap<String, Boolean> votes = guiltyVotes.computeIfAbsent(
                key,
                ignored -> new ConcurrentHashMap<>()
        );
        if (votes.putIfAbsent(playerToken, guilty) != null) {
            throw new RoomRuleException("Player already submitted a guilty vote.");
        }

        if (votes.size() < room.getAlivePlayers().size()) {
            return new VotePhaseResult(false, room.getNominatedPlayerToken(), false);
        }

        long guiltyCount = votes.values().stream().filter(Boolean::booleanValue).count();
        boolean executed = guiltyCount > room.getAlivePlayers().size() / 2;
        String targetToken = room.getNominatedPlayerToken();
        guiltyVotes.remove(key);
        room.finishGuiltyVote(executed, Instant.now());
        roomService.resolveWaitersForCurrentPhase(room, Map.of(
                "targetToken", targetToken,
                "executed", executed
        ));
        return new VotePhaseResult(true, targetToken, executed);
    }

    private void validatePhase(Room room, GamePhase expectedPhase) {
        if (room.getGamePhase() != expectedPhase) {
            throw new RoomRuleException("Action is not allowed during " + room.getGamePhase() + ".");
        }
    }

    private void validateAlivePlayer(Room room, String playerToken) {
        if (!room.hasAlivePlayer(playerToken)) {
            throw new RoomRuleException("Player token must belong to a living player in this room.");
        }
    }

    private String resolveMostVotedTarget(Map<String, String> votes) {
        Map<String, Long> counts = votes.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator
                        .comparing(Map.Entry<String, Long>::getValue)
                        .thenComparing(Map.Entry::getKey))
                .orElseThrow()
                .getKey();
    }

    private String voteKey(Room room, String type) {
        return room.getCode() + ":" + room.getDayTurn() + ":" + type;
    }
}
