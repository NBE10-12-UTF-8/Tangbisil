package com.back.domain.trend.dedup;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class MessageDuplicateChecker {

    private static final Duration KEY_TTL = Duration.ofDays(2);
    private static final String FINGERPRINT_KEY_PREFIX = "trend:fingerprints:";
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final RedisTemplate<String, String> redisTemplate;

    public MessageDuplicateChecker(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDuplicate(LocalDate date, UUID senderMemberId, String content) {
        if (senderMemberId == null || content == null || content.isBlank()) {
            return false;
        }

        String key = FINGERPRINT_KEY_PREFIX + date;
        Long added = redisTemplate.opsForSet().add(key, fingerprint(senderMemberId, content));
        if (added != null && added > 0L) {
            redisTemplate.expire(key, KEY_TTL);
        }

        return added != null && added == 0L;
    }

    private String fingerprint(UUID senderMemberId, String content) {
        String normalized = senderMemberId + "|" + WHITESPACE_PATTERN.matcher(content).replaceAll("").toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }
}
