package com.masterminds.night;

import com.masterminds.character.CharacterRole;
import com.masterminds.game.GamePhase;
import com.masterminds.player.Player;
import com.masterminds.room.Room;
import com.masterminds.room.RoomRuleException;
import com.masterminds.room.RoomService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NightService {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> killTargets =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> investigations =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> protections =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> doctorProtectionCounts = new ConcurrentHashMap<>();

    private final RoomService roomService;

    public NightService(RoomService roomService) {
        this.roomService = roomService;
    }

    public NightActionResult nightKill(String roomCode, String playerToken, String targetToken) {
        Room room = roomService.getRoom(roomCode);
        Player actor = validateActor(room, playerToken, CharacterRole.MAFIA);
        validateAliveTarget(room, targetToken);
        putOnce(killTargets, room, actor.playerToken(), targetToken, "Mafia already selected a kill target.");
        return resolveIfReady(room, targetToken, null);
    }

    public NightActionResult nightInvestigate(String roomCode, String playerToken, String targetToken) {
        Room room = roomService.getRoom(roomCode);
        Player actor = validateActor(room, playerToken, CharacterRole.DETECTIVE);
        Player target = validateAliveTarget(room, targetToken);
        putOnce(investigations, room, actor.playerToken(), targetToken, "Detective already investigated this night.");
        return resolveIfReady(room, targetToken, target.role() == CharacterRole.MAFIA);
    }

    public NightActionResult nightProtect(String roomCode, String playerToken, String targetToken) {
        Room room = roomService.getRoom(roomCode);
        Player actor = validateActor(room, playerToken, CharacterRole.DOCTOR);
        validateAliveTarget(room, targetToken);
        String doctorKey = doctorUseKey(room, actor.playerToken());
        if (doctorProtectionCounts.getOrDefault(doctorKey, 0) >= 3) {
            throw new RoomRuleException("Doctor can protect up to three times per game.");
        }
        putOnce(protections, room, actor.playerToken(), targetToken, "Doctor already protected this night.");
        doctorProtectionCounts.merge(doctorKey, 1, Integer::sum);
        return resolveIfReady(room, targetToken, null);
    }

    private NightActionResult resolveIfReady(Room room, String currentTargetToken, Boolean mafia) {
        if (!allRequiredActionsSubmitted(room)) {
            return new NightActionResult(false, currentTargetToken, null, false, mafia);
        }

        String key = nightKey(room);
        String killTarget = resolveMostVotedTarget(killTargets.getOrDefault(key, new ConcurrentHashMap<>()));
        String protectedTarget = resolveMostVotedTarget(protections.getOrDefault(key, new ConcurrentHashMap<>()));
        boolean protectedFromKill = killTarget != null && killTarget.equals(protectedTarget);
        String killedToken = protectedFromKill ? null : killTarget;
        if (killedToken != null) {
            room.killPlayer(killedToken);
        }

        killTargets.remove(key);
        investigations.remove(key);
        protections.remove(key);
        roomService.finishNightFromSystem(room.getCode(), Map.of(
                "targetToken", killTarget == null ? "" : killTarget,
                "killedToken", killedToken == null ? "" : killedToken,
                "protected", protectedFromKill
        ));
        return new NightActionResult(true, killTarget, killedToken, protectedFromKill, mafia);
    }

    private boolean allRequiredActionsSubmitted(Room room) {
        String key = nightKey(room);
        Map<String, String> kills = killTargets.getOrDefault(key, new ConcurrentHashMap<>());
        Map<String, String> detects = investigations.getOrDefault(key, new ConcurrentHashMap<>());
        Map<String, String> protects = protections.getOrDefault(key, new ConcurrentHashMap<>());

        return room.getAlivePlayers().stream()
                .filter(player -> player.role() == CharacterRole.MAFIA)
                .allMatch(player -> kills.containsKey(player.playerToken()))
                && room.getAlivePlayers().stream()
                .filter(player -> player.role() == CharacterRole.DETECTIVE)
                .allMatch(player -> detects.containsKey(player.playerToken()))
                && room.getAlivePlayers().stream()
                .filter(player -> player.role() == CharacterRole.DOCTOR)
                .allMatch(player -> protects.containsKey(player.playerToken()));
    }

    private Player validateActor(Room room, String playerToken, CharacterRole role) {
        if (room.getGamePhase() != GamePhase.NIGHT) {
            throw new RoomRuleException("Night actions can only be submitted during NIGHT.");
        }
        Player actor = room.findPlayer(playerToken)
                .orElseThrow(() -> new RoomRuleException("Player token does not belong to this room."));
        if (!actor.alive()) {
            throw new RoomRuleException("Dead players cannot act at night.");
        }
        if (actor.role() != role) {
            throw new RoomRuleException("This player cannot perform that night action.");
        }
        return actor;
    }

    private Player validateAliveTarget(Room room, String targetToken) {
        Player target = room.findPlayer(targetToken)
                .orElseThrow(() -> new RoomRuleException("Target token does not belong to this room."));
        if (!target.alive()) {
            throw new RoomRuleException("Target must be alive.");
        }
        return target;
    }

    private void putOnce(
            ConcurrentHashMap<String, ConcurrentHashMap<String, String>> store,
            Room room,
            String actorToken,
            String targetToken,
            String duplicateMessage
    ) {
        ConcurrentHashMap<String, String> actions = store.computeIfAbsent(
                nightKey(room),
                ignored -> new ConcurrentHashMap<>()
        );
        if (actions.putIfAbsent(actorToken, targetToken) != null) {
            throw new RoomRuleException(duplicateMessage);
        }
    }

    private String resolveMostVotedTarget(Map<String, String> targets) {
        if (targets.isEmpty()) {
            return null;
        }
        Map<String, Long> counts = targets.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator
                        .comparing(Map.Entry<String, Long>::getValue)
                        .thenComparing(Map.Entry::getKey))
                .orElseThrow()
                .getKey();
    }

    private String nightKey(Room room) {
        return room.getCode() + ":" + room.getNightNumber() + ":night";
    }

    private String doctorUseKey(Room room, String doctorToken) {
        return room.getCode() + ":" + doctorToken + ":doctor";
    }
}
