package com.masterminds.room;

import com.masterminds.player.Player;
import com.masterminds.room.dto.CreateRoomResponse;
import com.masterminds.room.dto.JoinRoomRequest;
import com.masterminds.room.dto.JoinRoomResponse;
import com.masterminds.room.dto.RoleAssignmentResponse;
import com.masterminds.room.dto.RoomResponse;
import com.masterminds.room.dto.StartRoomRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRoomResponse createRoom() {
        Room room = roomService.createRoom();
        return new CreateRoomResponse(room.getCode());
    }

    @PostMapping("/{code}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinRoomResponse joinRoom(
            @PathVariable String code,
            @RequestBody JoinRoomRequest request
    ) {
        Player player = roomService.joinRoom(code, request.name());
        return RoomMapper.toJoinRoomResponse(roomService.getRoom(code), player);
    }

    @GetMapping("/{code}")
    public RoomResponse getRoom(@PathVariable String code) {
        return RoomMapper.toRoomResponse(roomService.getRoom(code));
    }

    @PostMapping("/{code}/start")
    public RoomResponse startRoom(
            @PathVariable String code,
            @RequestBody StartRoomRequest request
    ) {
        return RoomMapper.toRoomResponse(roomService.startRoom(code, request.playerToken()));
    }

    @GetMapping("/{code}/players/{playerToken}/assignment")
    public RoleAssignmentResponse getPlayerAssignment(
            @PathVariable String code,
            @PathVariable String playerToken
    ) {
        return RoomMapper.toRoleAssignmentResponse(
                roomService.getRoom(code),
                roomService.getPlayerAssignment(code, playerToken)
        );
    }
}
