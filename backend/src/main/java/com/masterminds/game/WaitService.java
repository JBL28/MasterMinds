package com.masterminds.game;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class WaitService {

    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();

    public Map<String, Object> waitForNextPhase(String roomCode, String playerToken) {
        String key = roomCode + ":" + playerToken;
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pending.put(key, future);
        try {
            return future.get(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            pending.remove(key);
            return Map.of("phase", "TIMEOUT", "message", "대기 시간이 초과되었습니다.");
        }
    }

    public void resolveAll(String roomCode, Map<String, Object> payload) {
        String prefix = roomCode + ":";
        pending.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().complete(payload);
                return true;
            }
            return false;
        });
    }

    public void resolve(String roomCode, String playerToken, Map<String, Object> payload) {
        String key = roomCode + ":" + playerToken;
        CompletableFuture<Map<String, Object>> future = pending.remove(key);
        if (future != null) {
            future.complete(payload);
        }
    }
}
