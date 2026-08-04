package com.back.domain.trend.dedup;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;

@Component
public class MessageDuplicateChecker {

    private static final Duration KEY_TTL = Duration.ofDays(2);
    private static final String FINGERPRINT_KEY_PREFIX = "trend:fingerprints:";

    private final RedisTemplate<String, String> redisTemplate;

    public MessageDuplicateChecker(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDuplicate(LocalDate date, String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        String key = FINGERPRINT_KEY_PREFIX + date;
        Long added = redisTemplate.opsForSet().add(key, fingerprint(content));
        redisTemplate.expire(key, KEY_TTL);

        return added != null && added == 0L;
    }

    private String fingerprint(String content) {
        String normalized = content.replaceAll("\\s+", "").toLowerCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }
}
