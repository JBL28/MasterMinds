package com.masterminds.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
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

        mockMvc.perform(get("/api/rooms/{code}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(roomCode))
                .andExpect(jsonPath("$.status").value("LOBBY"))
                .andExpect(jsonPath("$.players", hasSize(1)))
                .andExpect(jsonPath("$.players[0].playerToken").value(playerToken))
                .andExpect(jsonPath("$.players[0].name").value("Alice"))
                .andExpect(jsonPath("$.players[0].host").value(true));

        mockMvc.perform(post("/api/rooms/{code}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + playerToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_GAME"));
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

        mockMvc.perform(post("/api/rooms/{code}/start", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerToken\":\"" + host.get("playerToken").asText() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/{code}/join", roomCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob\"}"))
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
}
