package com.masterminds.room.dto;

import com.masterminds.character.CharacterRole;

public record RoleAssignmentResponse(
        String roomCode,
        String playerToken,
        CharacterRole role,
        String displayName,
        String description
) {
}
