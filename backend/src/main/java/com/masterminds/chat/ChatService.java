package com.masterminds.chat;

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
        DayMessage dayMessage = new DayMessage(
                player.playerToken(),
                player.name(),
                room.getDayTurn(),
                normalizedMessage,
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
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + room.getCode() + "/messages",
                Map.of("type", "DAY_MESSAGES_REVEALED", "messages", revealedMessages)
        );
        roomService.advancePhaseFromSystem(room.getCode(), Map.of("messages", revealedMessages));
        return new DayMessageResult(true, revealedMessages);
    }

    private String dayMessageKey(Room room) {
        return room.getCode() + ":" + room.getDayTurn();
    }

    private List<DayMessage> orderedMessages(Map<String, DayMessage> messages) {
        return new ArrayList<>(messages.values()).stream()
                .sorted(Comparator.comparing(DayMessage::submittedAt))
                .toList();
    }
}
