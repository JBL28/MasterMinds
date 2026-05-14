package com.masterminds.chat;

import com.masterminds.player.Player;
import com.masterminds.room.Room;
import com.masterminds.room.RoomRuleException;
import com.masterminds.room.RoomService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class CemeteryMessageService {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public CemeteryMessageService(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    public CemeteryMessage sendCemeteryMessage(String roomCode, String playerToken, String message) {
        Room room = roomService.getRoom(roomCode);
        Player player = room.findPlayer(playerToken)
                .orElseThrow(() -> new RoomRuleException("Player token does not belong to this room."));
        if (player.alive()) {
            throw new RoomRuleException("Only dead players can send cemetery messages.");
        }

        String normalizedMessage = Optional.ofNullable(message)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new RoomRuleException("Message is required."));
        CemeteryMessage cemeteryMessage = new CemeteryMessage(
                player.playerToken(),
                player.name(),
                normalizedMessage,
                Instant.now()
        );
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + room.getCode() + "/cemetery",
                Map.of("type", "CEMETERY_MESSAGE", "message", cemeteryMessage)
        );
        return cemeteryMessage;
    }
}
