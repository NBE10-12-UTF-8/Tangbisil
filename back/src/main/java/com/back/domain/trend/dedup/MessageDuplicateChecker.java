package com.back.domain.trend.dedup;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// 오늘 하루 동안 이미 본 메시지의 MinHash 시그니처를 Redis에 쌓아두고,
// 새 메시지가 그중 하나와 90% 이상 겹치면 "복사-붙여넣기로 도배된 메시지"로 보고 집계에서 제외한다.
@Component
public class MessageDuplicateChecker {

    // 하루치 서명을 무한정 쌓아두지 않기 위한 상한선(오래된 것부터 버림).
    // 완벽한 전수 비교는 아니지만, 도배는 보통 짧은 시간에 몰리므로 최근 것과의 비교만으로도 충분하다.
    private static final int MAX_RECENT_SIGNATURES = 2000;
    private static final Duration KEY_TTL = Duration.ofDays(7);
    private static final String SIGNATURE_KEY_PREFIX = "trend:signatures:";

    private final RedisTemplate<String, String> redisTemplate;
    private final MinHashDeduplicator minHashDeduplicator;

    public MessageDuplicateChecker(RedisTemplate<String, String> redisTemplate, MinHashDeduplicator minHashDeduplicator) {
        this.redisTemplate = redisTemplate;
        this.minHashDeduplicator = minHashDeduplicator;
    }

    public boolean isDuplicate(LocalDate date, String content) {
        if (!minHashDeduplicator.canFingerprint(content)) {
            return false;
        }

        long[] signature = minHashDeduplicator.computeSignature(content);
        String key = SIGNATURE_KEY_PREFIX + date;

        List<String> recentSignatures = redisTemplate.opsForList().range(key, 0, -1);
        if (recentSignatures != null) {
            for (String stored : recentSignatures) {
                if (minHashDeduplicator.isDuplicate(signature, deserialize(stored))) {
                    return true;
                }
            }
        }

        redisTemplate.opsForList().leftPush(key, serialize(signature));
        redisTemplate.opsForList().trim(key, 0, MAX_RECENT_SIGNATURES - 1);
        redisTemplate.expire(key, KEY_TTL);
        return false;
    }

    private String serialize(long[] signature) {
        return Arrays.stream(signature)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private long[] deserialize(String stored) {
        String[] parts = stored.split(",");
        long[] signature = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            signature[i] = Long.parseLong(parts[i]);
        }
        return signature;
    }
}
