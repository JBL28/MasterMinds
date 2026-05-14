package com.masterminds.character;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CharacterAssignmentService {

    private static final int MINIMUM_PLAYERS = 4;

    private final SecureRandom random = new SecureRandom();

    public List<CharacterRole> assignRoles(int playerCount) {
        if (playerCount < MINIMUM_PLAYERS) {
            throw new IllegalArgumentException("At least 4 players are required to assign characters.");
        }

        List<CharacterRole> roles = new ArrayList<>();
        roles.add(CharacterRole.MAFIA);
        if (playerCount >= 7) {
            roles.add(CharacterRole.MAFIA);
        }
        roles.add(CharacterRole.DETECTIVE);
        roles.add(CharacterRole.DOCTOR);

        if (playerCount >= 6) {
            roles.add(CharacterRole.FOOL);
            roles.add(CharacterRole.HYPNOTIST);
        }
        if (playerCount >= 8) {
            roles.add(CharacterRole.LAWYER);
        }

        while (roles.size() < playerCount) {
            roles.add(CharacterRole.CITIZEN);
        }

        Collections.shuffle(roles, random);
        return roles;
    }
}
