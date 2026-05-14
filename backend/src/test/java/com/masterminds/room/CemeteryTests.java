package com.masterminds.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterminds.chat.CemeteryMessage;
import com.masterminds.chat.CemeteryMessageService;
import com.masterminds.mcp.tool.WaitTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CemeteryTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CemeteryMessageService cemeteryMessageService;

    @Autowired
    private WaitTool waitTool;

    @Test
    void onlyDeadPlayersCanSendCemeteryMessages() throws Exception {
        Map<String, String> playersByRole = startFourPlayerGameAndMapRoles();
        String roomCode = playersByRole.get("ROOM");
        String hostToken = playersByRole.get("HOST");
        String mafiaToken = playersByRole.get("MAFIA");
        String detectiveToken = playersByRole.get("DETECTIVE");
        String doctorToken = playersByRole.get("DOCTOR");
        String citizenToken = playersByRole.get("CITIZEN");

        advanceToNight(roomCode, hostToken);
        nightAction(roomCode, "investigate", detectiveToken, mafiaToken).andExpect(status().isOk());
        nightAction(roomCode, "protect", doctorToken, detectiveToken).andExpect(status().isOk());
        nightAction(roomCode, "kill", mafiaToken, citizenToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.killedToken").value(citizenToken));

        assertThatThrownBy(() -> cemeteryMessageService.sendCemeteryMessage(roomCode, mafiaToken, "Still alive."))
                .isInstanceOf(RoomRuleException.class)
                .hasMessageContaining("Only dead players");

        CemeteryMessage message = cemeteryMessageService.sendCemeteryMessage(roomCode, citizenToken, "I know too much.");
        assertThat(message.playerToken()).isEqualTo(citizenToken);
        assertThat(message.message()).isEqualTo("I know too much.");
    }

    @Test
    void deadPlayerWaitReturnsDeadPhase() throws Exception {
        Map<String, String> playersByRole = startFourPlayerGameAndMapRoles();
        String roomCode = playersByRole.get("ROOM");
        String hostToken = playersByRole.get("HOST");
        String mafiaToken = playersByRole.get("MAFIA");
        String detectiveToken = playersByRole.get("DETECTIVE");
        String doctorToken = playersByRole.get("DOCTOR");
        String citizenToken = playersByRole.get("CITIZEN");

        advanceToNight(roomCode, hostToken);
        nightAction(roomCode, "investigate", detectiveToken, mafiaToken).andExpect(status().isOk());
        nightAction(roomCode, "protect", doctorToken, detectiveToken).andExpect(status().isOk());
        nightAction(roomCode, "kill", mafiaToken, citizenToken).andExpect(status().isOk());

        Map<String, Object> waitResponse = waitTool.wait(roomCode, citizenToken);
        assertThat(waitResponse).containsEntry("phase", "DEAD");
        assertThat(waitResponse.get("message")).asString().contains("Cemetery chat");
    }

    private Map<String, String> startFourPlayerGameAndMapRoles() throws Exception {
        String roomCode = createRoom();
        JsonNode alice = joinRoom(roomCode, "Alice");
        JsonNode bob = joinRoom(roomCode, "Bob");
        JsonNode carol = joinRoom(roomCode, "Carol");
        JsonNode dave = joinRoom(roomCode, "Dave");
        String hostToken = alice.get("playerToken").asText();
        startRoom(roomCode, hostToken);

        Map<String, String> playersByRole = new LinkedHashMap<>();
        playersByRole.put("ROOM", roomCode);
        playersByRole.put("HOST", hostToken);
        putRole(playersByRole, roomCode, alice.get("playerToken").asText());
        putRole(playersByRole, roomCode, bob.get("playerToken").asText());
        putRole(playersByRole, roomCode, carol.get("playerToken").asText());
        putRole(playersByRole, roomCode, dave.get("playerToken").asText());
        return playersByRole;
    }

    private String createRoom() throws Exception {
        String content = mockMvc.perform(post("/api/rooms"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content).get("roomCode").asText();
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
                .andExpect(jsonPath("$.phaseEndsAt", notNullValue()));
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
                .andExpect(jsonPath("$.dayTurn").value(expectedDayTurn));
    }

    private org.springframework.test.web.servlet.ResultActions nightAction(
            String roomCode,
            String action,
            String playerToken,
            String targetToken
    ) throws Exception {
        return mockMvc.perform(post("/api/rooms/{code}/night/{action}", roomCode, action)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerToken\":\"" + playerToken + "\",\"targetToken\":\"" + targetToken + "\"}"));
    }
}
