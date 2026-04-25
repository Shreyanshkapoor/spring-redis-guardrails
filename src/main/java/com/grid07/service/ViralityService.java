package com.grid07.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class ViralityService {

    private static final String VIRALITY_KEY = "post:%d:virality_score";

    private final StringRedisTemplate redis;

    public void recordBotReply(Long postId) {
        incrementScore(postId, 1L);
    }

    public void recordHumanLike(Long postId) {
        incrementScore(postId, 20L);
    }

    public void recordHumanComment(Long postId) {
        incrementScore(postId, 50L);
    }

    public Long getScore(Long postId) {
        String raw = redis.opsForValue().get(viralityKey(postId));
        return raw == null ? 0L : Long.parseLong(raw);
    }



    private void incrementScore(Long postId, Long delta) {
        Long newScore = redis.opsForValue().increment(viralityKey(postId), delta);
        log.debug("[Virality] post:{} score → {}", postId, newScore);
    }

    private String viralityKey(Long postId) {
        return String.format(VIRALITY_KEY, postId);
    }
}
