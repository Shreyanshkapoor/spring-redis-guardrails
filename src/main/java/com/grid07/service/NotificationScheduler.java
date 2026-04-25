package com.grid07.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final String PENDING_NOTIFS_PATTERN = "user:*:pending_notifs";

    private final StringRedisTemplate redis;

    @Scheduled(fixedRate = 300_000)
    public void sweepPendingNotifications() {
        log.info("[Sweeper] Starting notification sweep...");

        Set<String> keys = redis.keys(PENDING_NOTIFS_PATTERN);

        if (keys == null || keys.isEmpty()) {
            log.info("[Sweeper] No pending notifications found.");
            return;
        }

        for (String key : keys) {
            processPendingNotifications(key);
        }

        log.info("[Sweeper] Sweep complete. Processed {} users.", keys.size());
    }


    private void processPendingNotifications(String pendingKey) {
        // Extract userId from "user:{id}:pending_notifs"
        String userId = pendingKey.split(":")[1];

        // Pop ALL messages atomically: LRANGE + DEL
        List<String> messages = redis.opsForList().range(pendingKey, 0, -1);
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // Delete the list right away so new pushes after this point start fresh
        redis.delete(pendingKey);

        int total = messages.size();

        // Extract the first bot name from the first message for the summary
        String firstBotName = extractBotName(messages.get(0));
        int others = total - 1;

        if (others <= 0) {
            log.info("[Sweeper] Summarized Push Notification to User {}: {} interacted with your posts.",
                    userId, firstBotName);
        } else {
            log.info("[Sweeper] Summarized Push Notification to User {}: {} and [{}] others interacted with your posts.",
                    userId, firstBotName, others);
        }
    }


    private String extractBotName(String message) {
        try {

            String after = message.substring("Bot ".length());
            return after.substring(0, after.indexOf(" replied"));
        } catch (Exception e) {
            return "Unknown Bot";
        }
    }
}
