package com.masterminds.chat;

import com.masterminds.character.CharacterRole;
import com.masterminds.game.GamePhase;
import com.masterminds.player.Player;
import com.masterminds.room.Room;
import com.masterminds.room.RoomRuleException;
import com.masterminds.room.RoomService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, DayMessage>> pendingDayMessages =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> hypnosisMessages =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> hypnotistUseCounts = new ConcurrentHashMap<>();

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatService(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    public DayMessageResult sendDayMessage(String roomCode, String playerToken, String message) {
        Room room = roomService.getRoom(roomCode);
        if (room.getGamePhase() != GamePhase.DAY_CHAT) {
            throw new RoomRuleException("Day messages can only be submitted during DAY_CHAT.");
        }

        Player player = room.findPlayer(playerToken)
                .orElseThrow(() -> new RoomRuleException("Player token does not belong to this room."));
        String normalizedMessage = Optional.ofNullable(message)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new RoomRuleException("Message is required."));

        String key = dayMessageKey(room);
        ConcurrentHashMap<String, DayMessage> messages = pendingDayMessages.computeIfAbsent(
                key,
                ignored -> new ConcurrentHashMap<>()
        );
        String finalMessage = Optional.ofNullable(hypnosisMessages.get(key))
                .map(values -> values.remove(player.playerToken()))
                .orElse(normalizedMessage);
        DayMessage dayMessage = new DayMessage(
                player.playerToken(),
                player.name(),
                room.getDayTurn(),
                finalMessage,
                Instant.now()
        );
        DayMessage previous = messages.putIfAbsent(player.playerToken(), dayMessage);
        if (previous != null) {
            throw new RoomRuleException("Player already submitted a day message for this turn.");
        }

        if (messages.size() < room.getPlayers().size()) {
            return new DayMessageResult(false, List.of());
        }

        List<DayMessage> revealedMessages = orderedMessages(messages);
        pendingDayMessages.remove(key);
        hypnosisMessages.remove(key);
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + room.getCode() + "/messages",
                Map.of("type", "DAY_MESSAGES_REVEALED", "messages", revealedMessages)
        );
        roomService.advancePhaseFromSystem(room.getCode(), Map.of("messages", revealedMessages));
        return new DayMessageResult(true, revealedMessages);
    }

    public DayMessageResult hypnotize(
            String roomCode,
            String hypnotistToken,
            String targetToken,
            String hypnotistMessage,
            String replacementMessage
    ) {
        Room room = roomService.getRoom(roomCode);
        if (room.getGamePhase() != GamePhase.DAY_CHAT) {
            throw new RoomRuleException("Hypnosis can only be used during DAY_CHAT.");
        }

        Player hypnotist = room.findPlayer(hypnotistToken)
                .orElseThrow(() -> new RoomRuleException("Player token does not belong to this room."));
        if (!hypnotist.alive() || hypnotist.role() != CharacterRole.HYPNOTIST) {
            throw new RoomRuleException("Only a living hypnotist can use hypnosis.");
        }

        Player target = room.findPlayer(targetToken)
                .orElseThrow(() -> new RoomRuleException("Target token does not belong to this room."));
        if (!target.alive()) {
            throw new RoomRuleException("Target must be alive.");
        }
        if (target.playerToken().equals(hypnotist.playerToken())) {
            throw new RoomRuleException("Hypnotist cannot target themselves.");
        }

        String replacement = Optional.ofNullable(replacementMessage)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new RoomRuleException("Replacement message is required."));
        String key = dayMessageKey(room);
        if (pendingDayMessages.getOrDefault(key, new ConcurrentHashMap<>()).containsKey(target.playerToken())) {
            throw new RoomRuleException("Target already submitted a day message for this turn.");
        }
        String useKey = hypnotistUseKey(room, hypnotist.playerToken());
        if (hypnotistUseCounts.getOrDefault(useKey, 0) >= 3) {
            throw new RoomRuleException("Hypnotist can use hypnosis up to three times per game.");
        }

        hypnosisMessages.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                .put(target.playerToken(), replacement);
        hypnotistUseCounts.merge(useKey, 1, Integer::sum);
        return sendDayMessage(roomCode, hypnotistToken, hypnotistMessage);
    }

    private String dayMessageKey(Room room) {
        return room.getCode() + ":" + room.getDayTurn();
    }

    private String hypnotistUseKey(Room room, String hypnotistToken) {
        return room.getCode() + ":" + hypnotistToken + ":hypnotist";
    }

    private List<DayMessage> orderedMessages(Map<String, DayMessage> messages) {
        return new ArrayList<>(messages.values()).stream()
                .sorted(Comparator.comparing(DayMessage::submittedAt))
                .toList();
    }
}
