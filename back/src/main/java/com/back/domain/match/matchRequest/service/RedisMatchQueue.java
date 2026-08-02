package com.back.domain.match.matchRequest.service;

import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.member.member.entity.Industry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class RedisMatchQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisMatchQueue.class);
    private static final String QUEUE_KEY_PREFIX = "match:queue:";
    private final RedisTemplate<String, String> redisTemplate;

    public RedisMatchQueue(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void add(Industry industry, Situation situation, UUID matchRequestId, long score) {
        String key = getQueueKey(industry, situation);
        String value = matchRequestId.toString();
        try {
            redisTemplate.opsForZSet().add(key, value, score);
            log.info("[RedisMatchQueue] 대기열 적재 완료 (ZADD) - key: {}, value: {}, score: {}", key, value, score);
        } catch (Exception e) {
            log.error("[RedisMatchQueue] 대기열 적재 실패 - key: {}, value: {}", key, value, e);
            throw e;
        }
    }

    public void remove(Industry industry, Situation situation, UUID matchRequestId) {
        String key = getQueueKey(industry, situation);
        String value = matchRequestId.toString();
        try {
            redisTemplate.opsForZSet().remove(key, value);
            log.info("[RedisMatchQueue] 대기열 제거 완료 (ZREM) - key: {}, value: {}", key, value);
        } catch (Exception e) {
            log.error("[RedisMatchQueue] 대기열 제거 실패 - key: {}, value: {}", key, value, e);
            throw e;
        }
    }

    public Optional<UUID> getOldest(Industry industry, Situation situation) {
        String key = getQueueKey(industry, situation);
        try {
            Set<String> range = redisTemplate.opsForZSet().range(key, 0, 0);

            if (range == null || range.isEmpty()) {
                return Optional.empty();
            }

            String oldestId = range.iterator().next();
            return Optional.of(UUID.fromString(oldestId));
        } catch (Exception e) {
            log.error("[RedisMatchQueue] 대기열 조회 실패 (ZRANGE) - key: {}", key, e);
            throw e;
        }
    }

    private String getQueueKey(Industry industry, Situation situation) {
        return QUEUE_KEY_PREFIX + industry.name() + ":" + situation.name();
    }
}