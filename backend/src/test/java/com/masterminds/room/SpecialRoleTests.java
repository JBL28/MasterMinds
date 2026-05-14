package com.masterminds.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SpecialRoleTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void hypnotistReplacesTargetDayMessageBeforeReveal() throws Exception {
        GameState game = startGame(6);
        String hypnotistToken = game.playersByRole().get("HYPNOTIST");
        String targetToken = game.playersByRole().get("CITIZEN");
        String replacement = "I confess to being confused.";

        postJson(
                "/api/rooms/" + game.roomCode() + "/hypnotize",
                Map.of(
                        "playerToken", hypnotistToken,
                        "targetToken", targetToken,
                        "message", "I will listen carefully.",
                        "replacementMessage", replacement
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false));

        JsonNode finalResponse = null;
        for (String playerToken : game.playerTokens()) {
            if (playerToken.equals(hypnotistToken)) {
                continue;
            }
            JsonNode response = objectMapper.readTree(postJson(
                    "/api/rooms/" + game.roomCode() + "/day-messages",
                    Map.of("playerToken", playerToken, "message", "Original message from " + playerToken)
            )
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            if (response.get("revealed").asBoolean()) {
                finalResponse = response;
            }
        }

        assertThat(finalResponse).isNotNull();
        JsonNode targetMessage = findMessage(finalResponse.get("messages"), targetToken);
        assertThat(targetMessage.get("message").asText()).isEqualTo(replacement);
    }

    @Test
    void doctorCannotProtectMoreThanThreeTimes() throws Exception {
        GameState game = startGame(4);
        String hostToken = game.hostToken();
        String mafiaToken = game.playersByRole().get("MAFIA");
        String detectiveToken = game.playersByRole().get("DETECTIVE");
        String doctorToken = game.playersByRole().get("DOCTOR");
        String citizenToken = game.playersByRole().get("CITIZEN");

        for (int i = 0; i < 3; i++) {
            advanceToNight(game.roomCode(), hostToken);
            nightAction(game.roomCode(), "investigate", detectiveToken, mafiaToken)
                    .andExpect(status().isOk());
            nightAction(game.roomCode(), "protect", doctorToken, citizenToken)
                    .andExpect(status().isOk());
            nightAction(game.roomCode(), "kill", mafiaToken, citizenToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolved").value(true))
                    .andExpect(jsonPath("$.protectedTarget").value(true));
        }

        advanceToNight(game.roomCode(), hostToken);
        nightAction(game.roomCode(), "investigate", detectiveToken, mafiaToken)
                .andExpect(status().isOk());
        nightAction(game.roomCode(), "protect", doctorToken, citizenToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Room rule violation"));
    }

    @Test
    void executingFoolEndsGameWithFoolResult() throws Exception {
        GameState game = startGame(6);
        String foolToken = game.playersByRole().get("FOOL");

        advanceToNomination(game.roomCode(), game.hostToken());
        for (int i = 0; i < game.playerTokens().size(); i++) {
            String playerToken = game.playerTokens().get(i);
            submitNomination(game.roomCode(), playerToken, foolToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolved").value(i == game.playerTokens().size() - 1));
        }

        postJson("/api/rooms/" + game.roomCode() + "/vote/last-words", Map.of("playerToken", foolToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt", notNullValue()));
        postJson("/api/rooms/" + game.roomCode() + "/vote/last-words/complete", Map.of("playerToken", foolToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamePhase").value("VOTE_GUILTY"));

        for (int i = 0; i < game.playerTokens().size(); i++) {
            String playerToken = game.playerTokens().get(i);
            postJson(
                    "/api/rooms/" + game.roomCode() + "/vote/guilty",
                    Map.of("playerToken", playerToken, "guilty", true)
            )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolved").value(i == game.playerTokens().size() - 1));
        }

        mockMvc.perform(get("/api/rooms/{code}", game.roomCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamePhase").value("GAME_OVER"))
                .andExpect(jsonPath("$.result").value("FOOL"));
    }

    private GameState startGame(int playerCount) throws Exception {
        String roomCode = createRoom();
        List<String> playerTokens = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            playerTokens.add(joinRoom(roomCode, "Player" + i));
        }
        String hostToken = playerTokens.get(0);
        postJson("/api/rooms/" + roomCode + "/start", Map.of("playerToken", hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_GAME"));

        Map<String, String> playersByRole = new LinkedHashMap<>();
        for (String playerToken : playerTokens) {
            String role = objectMapper.readTree(mockMvc.perform(get(
                            "/api/rooms/{code}/players/{playerToken}/assignment",
                            roomCode,
                            playerToken
                    ))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
                    .get("role")
                    .asText();
            playersByRole.put(role, playerToken);
        }
        return new GameState(roomCode, hostToken, playerTokens, playersByRole);
    }

    private String createRoom() throws Exception {
        String content = mockMvc.perform(post("/api/rooms"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content).get("roomCode").asText();
    }

    private String joinRoom(String roomCode, String name) throws Exception {
        String content = postJson("/api/rooms/" + roomCode + "/join", Map.of("name", name))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content).get("playerToken").asText();
    }

    private void advanceToNomination(String roomCode, String hostToken) throws Exception {
        advancePhase(roomCode, hostToken, "DAY_CHAT", 2);
        advancePhase(roomCode, hostToken, "DAY_CHAT", 3);
        advancePhase(roomCode, hostToken, "VOTE_NOMINATE", 3);
    }

    private void advanceToNight(String roomCode, String hostToken) throws Exception {
        advanceToNomination(roomCode, hostToken);
        advancePhase(roomCode, hostToken, "FINAL_SPEECH", 3);
        advancePhase(roomCode, hostToken, "VOTE_GUILTY", 3);
        advancePhase(roomCode, hostToken, "NIGHT", 3);
    }

    private void advancePhase(
            String roomCode,
            String playerToken,
            String expectedPhase,
            int expectedDayTurn
    ) throws Exception {
        postJson("/api/rooms/" + roomCode + "/phase/advance", Map.of("playerToken", playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamePhase").value(expectedPhase))
                .andExpect(jsonPath("$.dayTurn").value(expectedDayTurn));
    }

    private org.springframework.test.web.servlet.ResultActions submitNomination(
            String roomCode,
            String playerToken,
            String targetToken
    ) throws Exception {
        return postJson(
                "/api/rooms/" + roomCode + "/vote/nominate",
                Map.of("playerToken", playerToken, "targetToken", targetToken)
        );
    }

    private org.springframework.test.web.servlet.ResultActions nightAction(
            String roomCode,
            String action,
            String playerToken,
            String targetToken
    ) throws Exception {
        return postJson(
                "/api/rooms/" + roomCode + "/night/" + action,
                Map.of("playerToken", playerToken, "targetToken", targetToken)
        );
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode findMessage(JsonNode messages, String playerToken) {
        for (JsonNode message : messages) {
            if (message.get("playerToken").asText().equals(playerToken)) {
                return message;
            }
        }
        throw new AssertionError("Message not found for player: " + playerToken);
    }

    private record GameState(
            String roomCode,
            String hostToken,
            List<String> playerTokens,
            Map<String, String> playersByRole
    ) {
    }
}
