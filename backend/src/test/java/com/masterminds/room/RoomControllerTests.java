package com.masterminds.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoomControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsRoomWithSixCharacterCode() throws Exception {
        mockMvc.perform(post("/api/rooms"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomCode", matchesPattern("[A-Z2-9]{6}")));
    }

    @Test
    void firstJoinedPlayerBecomesHostAndCanStartRoom() throws Exception {
        String roomCode = createRoom();
        JsonNode joinResponse = joinRoom(roomCode, "Alice");
        String playerToken = joinResponse.get("playerToken").asText();
        joinRoom(roomCode, "Bob");
        joinRoom(roomCode, "Carol");
        joinRoom(roomCode, "Dave");

        mockMvc.perform(get("/api/rooms/{code}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.status").value("LOBBY"))
                .andExpect(jsonPath("$.players", hasSize(4)))
                .andExpect(jsonPath("$.players[0].playerToken").value(playerToken))
                .andExpect(jsonPath("$.players[0].name").value("Alice"))
                .andExpect(jsonPath("$.players[0].host").value(true));

        mockMvc.perform(post("/api/rooms/{code}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + playerToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_GAME"))
                .andExpect(jsonPath("$.gamePhase").value("DAY_CHAT"))
                .andExpect(jsonPath("$.dayTurn").value(1))
                .andExpect(jsonPath("$.phaseEndsAt", notNullValue()));
    }

    @Test
    void nonHostCannotStartRoom() throws Exception {
        String roomCode = createRoom();
        joinRoom(roomCode, "Alice");
        JsonNode bob = joinRoom(roomCode, "Bob");

        mockMvc.perform(post("/api/rooms/{code}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + bob.get("playerToken").asText() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Room rule violation"));
    }

    @Test
    void playerCannotJoinAfterGameStarts() throws Exception {
        String roomCode = createRoom();
        JsonNode host = joinRoom(roomCode, "Alice");
        joinRoom(roomCode, "Bob");
        joinRoom(roomCode, "Carol");
        joinRoom(roomCode, "Dave");

        startRoom(roomCode, host.get("playerToken").asText());

        mockMvc.perform(post("/api/rooms/{code}/join", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Room rule violation"));
    }

    @Test
    void startAssignsPrivateRolesWithoutLeakingThemInRoomState() throws Exception {
        String roomCode = createRoom();
        JsonNode host = joinRoom(roomCode, "Alice");
        joinRoom(roomCode, "Bob");
        joinRoom(roomCode, "Carol");
        joinRoom(roomCode, "Dave");

        startRoom(roomCode, host.get("playerToken").asText());

        mockMvc.perform(get("/api/rooms/{code}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[0].role").doesNotExist());

        mockMvc.perform(get(
                        "/api/rooms/{code}/players/{playerToken}/assignment",
                        roomCode,
                        host.get("playerToken").asText()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.playerToken").value(host.get("playerToken").asText()))
                .andExpect(jsonPath("$.role", notNullValue()))
                .andExpect(jsonPath("$.displayName", notNullValue()))
                .andExpect(jsonPath("$.description", notNullValue()));
    }

    @Test
    void cannotStartRoomBeforeMinimumCharacterCount() throws Exception {
        String roomCode = createRoom();
        JsonNode host = joinRoom(roomCode, "Alice");
        joinRoom(roomCode, "Bob");
        joinRoom(roomCode, "Carol");

        mockMvc.perform(post("/api/rooms/{code}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + host.get("playerToken").asText() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Room rule violation"));
    }

    @Test
    void hostCanAdvanceThroughCorePhaseCycle() throws Exception {
        String roomCode = createRoom();
        JsonNode host = joinRoom(roomCode, "Alice");
        joinRoom(roomCode, "Bob");
        joinRoom(roomCode, "Carol");
        joinRoom(roomCode, "Dave");
        String hostToken = host.get("playerToken").asText();

        startRoom(roomCode, hostToken);

        advancePhase(roomCode, hostToken, "DAY_CHAT", 2);
        advancePhase(roomCode, hostToken, "DAY_CHAT", 3);
        advancePhase(roomCode, hostToken, "VOTE_NOMINATE", 3);
        advancePhase(roomCode, hostToken, "FINAL_SPEECH", 3);
        advancePhase(roomCode, hostToken, "VOTE_GUILTY", 3);
        advancePhase(roomCode, hostToken, "NIGHT", 3);
        advancePhase(roomCode, hostToken, "DAY_CHAT", 1);
    }

    @Test
    void nonHostCannotAdvancePhase() throws Exception {
        String roomCode = createRoom();
        JsonNode host = joinRoom(roomCode, "Alice");
        JsonNode bob = joinRoom(roomCode, "Bob");
        joinRoom(roomCode, "Carol");
        joinRoom(roomCode, "Dave");

        startRoom(roomCode, host.get("playerToken").asText());

        mockMvc.perform(post("/api/rooms/{code}/phase/advance", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + bob.get("playerToken").asText() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Room rule violation"));
    }

    @Test
    void dayMessagesRevealAfterEveryPlayerSubmitsAndAdvanceTurn() throws Exception {
        String roomCode = createRoom();
        JsonNode alice = joinRoom(roomCode, "Alice");
        JsonNode bob = joinRoom(roomCode, "Bob");
        JsonNode carol = joinRoom(roomCode, "Carol");
        JsonNode dave = joinRoom(roomCode, "Dave");

        startRoom(roomCode, alice.get("playerToken").asText());

        submitDayMessage(roomCode, alice.get("playerToken").asText(), "I am watching Bob.", false, 0);
        submitDayMessage(roomCode, bob.get("playerToken").asText(), "Alice is suspicious.", false, 0);
        submitDayMessage(roomCode, carol.get("playerToken").asText(), "I need more evidence.", false, 0);
        submitDayMessage(roomCode, dave.get("playerToken").asText(), "Let's compare claims.", true, 4);

        mockMvc.perform(get("/api/rooms/{code}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamePhase").value("DAY_CHAT"))
                .andExpect(jsonPath("$.dayTurn").value(2));
    }

    @Test
    void playerCannotSubmitDayMessageTwiceInSameTurn() throws Exception {
        String roomCode = createRoom();
        JsonNode alice = joinRoom(roomCode, "Alice");
        joinRoom(roomCode, "Bob");
        joinRoom(roomCode, "Carol");
        joinRoom(roomCode, "Dave");

        startRoom(roomCode, alice.get("playerToken").asText());
        submitDayMessage(roomCode, alice.get("playerToken").asText(), "First message.", false, 0);

        mockMvc.perform(post("/api/rooms/{code}/day-messages", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + alice.get("playerToken").asText() + "\",\"message\":\"Second\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Room rule violation"));
    }

    @Test
    void dayMessageIsRejectedOutsideDayChat() throws Exception {
        String roomCode = createRoom();
        JsonNode alice = joinRoom(roomCode, "Alice");
        joinRoom(roomCode, "Bob");
        joinRoom(roomCode, "Carol");
        joinRoom(roomCode, "Dave");
        String aliceToken = alice.get("playerToken").asText();

        startRoom(roomCode, aliceToken);
        advancePhase(roomCode, aliceToken, "DAY_CHAT", 2);
        advancePhase(roomCode, aliceToken, "DAY_CHAT", 3);
        advancePhase(roomCode, aliceToken, "VOTE_NOMINATE", 3);

        mockMvc.perform(post("/api/rooms/{code}/day-messages", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + aliceToken + "\",\"message\":\"Too late\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Room rule violation"));
    }

    @Test
    void nominationFinalSpeechAndGuiltyVoteCanExecutePlayer() throws Exception {
        String roomCode = createRoom();
        JsonNode alice = joinRoom(roomCode, "Alice");
        JsonNode bob = joinRoom(roomCode, "Bob");
        JsonNode carol = joinRoom(roomCode, "Carol");
        JsonNode dave = joinRoom(roomCode, "Dave");
        String aliceToken = alice.get("playerToken").asText();
        String bobToken = bob.get("playerToken").asText();
        String carolToken = carol.get("playerToken").asText();
        String daveToken = dave.get("playerToken").asText();

        startRoom(roomCode, aliceToken);
        advancePhase(roomCode, aliceToken, "DAY_CHAT", 2);
        advancePhase(roomCode, aliceToken, "DAY_CHAT", 3);
        advancePhase(roomCode, aliceToken, "VOTE_NOMINATE", 3);

        submitNomination(roomCode, aliceToken, bobToken, false);
        submitNomination(roomCode, bobToken, bobToken, false);
        submitNomination(roomCode, carolToken, bobToken, false);
        submitNomination(roomCode, daveToken, bobToken, true);

        mockMvc.perform(get("/api/rooms/{code}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamePhase").value("FINAL_SPEECH"))
                .andExpect(jsonPath("$.nominatedPlayerToken").value(bobToken));

        mockMvc.perform(post("/api/rooms/{code}/vote/last-words", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + bobToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt", notNullValue()));

        mockMvc.perform(post("/api/rooms/{code}/vote/last-words/complete", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + bobToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamePhase").value("VOTE_GUILTY"));

        submitGuiltyVote(roomCode, aliceToken, true, false, false);
        submitGuiltyVote(roomCode, bobToken, false, false, false);
        submitGuiltyVote(roomCode, carolToken, true, false, false);
        submitGuiltyVote(roomCode, daveToken, true, true, true);

        mockMvc.perform(get("/api/rooms/{code}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamePhase").value("NIGHT"))
                .andExpect(jsonPath("$.players[1].playerToken").value(bobToken))
                .andExpect(jsonPath("$.players[1].alive").value(false));
    }

    @Test
    void voteActionsAreRejectedInWrongPhase() throws Exception {
        String roomCode = createRoom();
        JsonNode alice = joinRoom(roomCode, "Alice");
        JsonNode bob = joinRoom(roomCode, "Bob");
        joinRoom(roomCode, "Carol");
        joinRoom(roomCode, "Dave");

        startRoom(roomCode, alice.get("playerToken").asText());

        mockMvc.perform(post("/api/rooms/{code}/vote/nominate", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + alice.get("playerToken").asText()
                                + "\",\"targetToken\":\"" + bob.get("playerToken").asText() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Room rule violation"));
    }

    @Test
    void nightActionsResolveWithDoctorProtection() throws Exception {
        String roomCode = createRoom();
        Map<String, String> playersByRole = startFourPlayerGameAndMapRoles(roomCode);
        String hostToken = playersByRole.get("HOST");
        String mafiaToken = playersByRole.get("MAFIA");
        String detectiveToken = playersByRole.get("DETECTIVE");
        String doctorToken = playersByRole.get("DOCTOR");
        String citizenToken = playersByRole.get("CITIZEN");

        advanceToNight(roomCode, hostToken);

        mockMvc.perform(post("/api/rooms/{code}/night/investigate", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + detectiveToken + "\",\"targetToken\":\"" + mafiaToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(false))
                .andExpect(jsonPath("$.mafia").value(true));

        mockMvc.perform(post("/api/rooms/{code}/night/protect", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + doctorToken + "\",\"targetToken\":\"" + citizenToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(false));

        mockMvc.perform(post("/api/rooms/{code}/night/kill", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + mafiaToken + "\",\"targetToken\":\"" + citizenToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.protectedTarget").value(true));

        assertPlayerAlive(roomCode, citizenToken, true);
    }

    @Test
    void nightKillExecutesUnprotectedTargetAndReturnsToDay() throws Exception {
        String roomCode = createRoom();
        Map<String, String> playersByRole = startFourPlayerGameAndMapRoles(roomCode);
        String hostToken = playersByRole.get("HOST");
        String mafiaToken = playersByRole.get("MAFIA");
        String detectiveToken = playersByRole.get("DETECTIVE");
        String doctorToken = playersByRole.get("DOCTOR");
        String citizenToken = playersByRole.get("CITIZEN");

        advanceToNight(roomCode, hostToken);

        mockMvc.perform(post("/api/rooms/{code}/night/investigate", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + detectiveToken + "\",\"targetToken\":\"" + mafiaToken + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/{code}/night/protect", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + doctorToken + "\",\"targetToken\":\"" + detectiveToken + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/{code}/night/kill", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + mafiaToken + "\",\"targetToken\":\"" + citizenToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.protectedTarget").value(false))
                .andExpect(jsonPath("$.killedToken").value(citizenToken));

        mockMvc.perform(get("/api/rooms/{code}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamePhase").value("DAY_CHAT"))
                .andExpect(jsonPath("$.dayTurn").value(1));
        assertPlayerAlive(roomCode, citizenToken, false);
    }

    @Test
    void nightActionRejectsWrongRole() throws Exception {
        String roomCode = createRoom();
        Map<String, String> playersByRole = startFourPlayerGameAndMapRoles(roomCode);
        String hostToken = playersByRole.get("HOST");
        String citizenToken = playersByRole.get("CITIZEN");
        String mafiaToken = playersByRole.get("MAFIA");

        advanceToNight(roomCode, hostToken);

        mockMvc.perform(post("/api/rooms/{code}/night/kill", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + citizenToken + "\",\"targetToken\":\"" + mafiaToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Room rule violation"));
    }

    private String createRoom() throws Exception {
        String content = mockMvc.perform(post("/api/rooms"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content).get("roomCode").asText();
    }

    private Map<String, String> startFourPlayerGameAndMapRoles(String roomCode) throws Exception {
        JsonNode alice = joinRoom(roomCode, "Alice");
        JsonNode bob = joinRoom(roomCode, "Bob");
        JsonNode carol = joinRoom(roomCode, "Carol");
        JsonNode dave = joinRoom(roomCode, "Dave");
        String hostToken = alice.get("playerToken").asText();
        startRoom(roomCode, hostToken);

        Map<String, String> playersByRole = new LinkedHashMap<>();
        playersByRole.put("HOST", hostToken);
        putRole(playersByRole, roomCode, alice.get("playerToken").asText());
        putRole(playersByRole, roomCode, bob.get("playerToken").asText());
        putRole(playersByRole, roomCode, carol.get("playerToken").asText());
        putRole(playersByRole, roomCode, dave.get("playerToken").asText());
        return playersByRole;
    }

    private void putRole(Map<String, String> playersByRole, String roomCode, String playerToken) throws Exception {
        String content = mockMvc.perform(get(
                        "/api/rooms/{code}/players/{playerToken}/assignment",
                        roomCode,
                        playerToken
                ))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        playersByRole.put(objectMapper.readTree(content).get("role").asText(), playerToken);
    }

    private void advanceToNight(String roomCode, String hostToken) throws Exception {
        advancePhase(roomCode, hostToken, "DAY_CHAT", 2);
        advancePhase(roomCode, hostToken, "DAY_CHAT", 3);
        advancePhase(roomCode, hostToken, "VOTE_NOMINATE", 3);
        advancePhase(roomCode, hostToken, "FINAL_SPEECH", 3);
        advancePhase(roomCode, hostToken, "VOTE_GUILTY", 3);
        advancePhase(roomCode, hostToken, "NIGHT", 3);
    }

    private void assertPlayerAlive(String roomCode, String playerToken, boolean alive) throws Exception {
        JsonNode room = objectMapper.readTree(mockMvc.perform(get("/api/rooms/{code}", roomCode))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        for (JsonNode player : room.get("players")) {
            if (player.get("playerToken").asText().equals(playerToken)) {
                org.assertj.core.api.Assertions.assertThat(player.get("alive").asBoolean()).isEqualTo(alive);
                return;
            }
        }
        throw new AssertionError("Player not found: " + playerToken);
    }

    private JsonNode joinRoom(String roomCode, String name) throws Exception {
        String content = mockMvc.perform(post("/api/rooms/{code}/join", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private void startRoom(String roomCode, String playerToken) throws Exception {
        mockMvc.perform(post("/api/rooms/{code}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + playerToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_GAME"))
                .andExpect(jsonPath("$.gamePhase").value("DAY_CHAT"))
                .andExpect(jsonPath("$.dayTurn").value(1));
    }

    private void advancePhase(
            String roomCode,
            String playerToken,
            String expectedPhase,
            int expectedDayTurn
    ) throws Exception {
        mockMvc.perform(post("/api/rooms/{code}/phase/advance", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + playerToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gamePhase").value(expectedPhase))
                .andExpect(jsonPath("$.dayTurn").value(expectedDayTurn))
                .andExpect(jsonPath("$.phaseStartedAt", notNullValue()))
                .andExpect(jsonPath("$.phaseEndsAt", notNullValue()));
    }

    private void submitDayMessage(
            String roomCode,
            String playerToken,
            String message,
            boolean expectedRevealed,
            int expectedMessageCount
    ) throws Exception {
        mockMvc.perform(post("/api/rooms/{code}/day-messages", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + playerToken + "\",\"message\":\"" + message + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(expectedRevealed))
                .andExpect(jsonPath("$.messages", hasSize(expectedMessageCount)));
    }

    private void submitNomination(
            String roomCode,
            String playerToken,
            String targetToken,
            boolean expectedResolved
    ) throws Exception {
        mockMvc.perform(post("/api/rooms/{code}/vote/nominate", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + playerToken + "\",\"targetToken\":\"" + targetToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(expectedResolved));
    }

    private void submitGuiltyVote(
            String roomCode,
            String playerToken,
            boolean guilty,
            boolean expectedResolved,
            boolean expectedExecuted
    ) throws Exception {
        mockMvc.perform(post("/api/rooms/{code}/vote/guilty", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + playerToken + "\",\"guilty\":" + guilty + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(expectedResolved))
                .andExpect(jsonPath("$.executed").value(expectedExecuted));
    }
}
