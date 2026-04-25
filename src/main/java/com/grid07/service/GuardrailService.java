package com.grid07.service;

import com.grid07.exception.GuardrailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuardrailService {

    // ── Redis key templates ───────────────────────────────────────────────────
    private static final String BOT_COUNT_KEY   = "post:%d:bot_count";
    private static final String COOLDOWN_KEY     = "cooldown:bot_%d:human_%d";

    // ── Limits ────────────────────────────────────────────────────────────────
    public static final int  HORIZONTAL_CAP     = 100;
    public static final int  VERTICAL_CAP       = 20;
    public static final long COOLDOWN_SECONDS   = 600L;   // 10 minutes

    private final StringRedisTemplate redis;


    public void checkAndIncrementBotCount(Long postId) {
        String key = botCountKey(postId);
        Long newCount = redis.opsForValue().increment(key);

        if (newCount == null) {
            // Should never happen with a healthy Redis, but be defensive.
            throw new GuardrailException("Redis error while checking bot count.");
        }

        if (newCount > HORIZONTAL_CAP) {
            // Roll back immediately so the counter stays correct.
            redis.opsForValue().decrement(key);
            log.warn("[Guardrail] Horizontal cap exceeded for post:{} (count={})", postId, newCount);
            throw new GuardrailException(
                    "Horizontal cap reached: post " + postId + " already has " + HORIZONTAL_CAP + " bot replies.");
        }

        log.debug("[Guardrail] Bot count for post:{} → {}", postId, newCount);
    }


    public void rollbackBotCount(Long postId) {
        redis.opsForValue().decrement(botCountKey(postId));
        log.debug("[Guardrail] Rolled back bot count for post:{}", postId);
    }

    public void checkVerticalCap(int depthLevel) {
        if (depthLevel > VERTICAL_CAP) {
            log.warn("[Guardrail] Vertical cap exceeded (depth={})", depthLevel);
            throw new GuardrailException(
                    "Vertical cap reached: comment thread cannot exceed " + VERTICAL_CAP + " levels deep.");
        }
    }


    public void checkCooldown(Long botId, Long humanId) {
        String key = cooldownKey(botId, humanId);

        // SET key "1" NX EX 600  — atomic, returns true only if key was absent
        Boolean wasAbsent = redis.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(COOLDOWN_SECONDS));

        if (Boolean.FALSE.equals(wasAbsent)) {
            log.warn("[Guardrail] Cooldown active: bot:{} → human:{}", botId, humanId);
            throw new GuardrailException(
                    "Cooldown active: Bot " + botId + " cannot interact with User " + humanId +
                    " again for another " + COOLDOWN_SECONDS + " seconds.");
        }

        log.debug("[Guardrail] Cooldown key set for bot:{} → human:{}", botId, humanId);
    }



    public Long getBotCount(Long postId) {
        String raw = redis.opsForValue().get(botCountKey(postId));
        return raw == null ? 0L : Long.parseLong(raw);
    }



    private String botCountKey(Long postId) {
        return String.format(BOT_COUNT_KEY, postId);
    }

    private String cooldownKey(Long botId, Long humanId) {
        return String.format(COOLDOWN_KEY, botId, humanId);
    }
}
