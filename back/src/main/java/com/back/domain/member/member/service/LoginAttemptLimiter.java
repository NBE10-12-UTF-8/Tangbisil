package com.back.domain.member.member.service;

import com.back.global.util.EmailNormalizer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LoginAttemptLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login:attempts:";

    private final RedisTemplate<String, String> redisTemplate;

    public LoginAttemptLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isBlocked(String email) {
        String value = redisTemplate.opsForValue().get(key(email));
        return value != null && Long.parseLong(value) >= MAX_ATTEMPTS;
    }

    public void recordFailure(String email) {
        String key = key(email);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    public void recordSuccess(String email) {
        redisTemplate.delete(key(email));
    }

    private String key(String email) {
        return KEY_PREFIX + EmailNormalizer.normalize(email);
    }
}
