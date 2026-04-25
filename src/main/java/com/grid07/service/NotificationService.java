package com.grid07.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;


@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String NOTIF_COOLDOWN_KEY  = "notif_cooldown:user_%d";
    private static final String PENDING_NOTIFS_KEY  = "user:%d:pending_notifs";
    private static final long   NOTIF_COOLDOWN_SEC  = 900L;

    private final StringRedisTemplate redis;


    public void handleBotInteractionNotification(Long userId, String botName, Long postId) {
        String cooldownKey = notifCooldownKey(userId);
        String pendingKey  = pendingNotifsKey(userId);
        String message     = String.format("Bot %s replied to your post (postId=%d)", botName, postId);

        Boolean cooldownActive = redis.hasKey(cooldownKey);

        if (Boolean.TRUE.equals(cooldownActive)) {
            redis.opsForList().rightPush(pendingKey, message);
            log.debug("[Notification] Queued for user:{} → \"{}\"", userId, message);
        } else {
            log.info("[Notification] Push Notification Sent to User:{} → \"{}\"", userId, message);
            redis.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(NOTIF_COOLDOWN_SEC));
        }
    }


    public String notifCooldownKey(Long userId) {
        return String.format(NOTIF_COOLDOWN_KEY, userId);
    }

    public String pendingNotifsKey(Long userId) {
        return String.format(PENDING_NOTIFS_KEY, userId);
    }
}
