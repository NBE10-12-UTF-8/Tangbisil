package com.back.domain.notification.service;

import com.back.domain.notification.dto.MatchNotificationDto;
import com.back.standard.util.Ut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchNotificationService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final Duration TTL = Duration.ofDays(3);

    public void notifyMatchSuccess(UUID memberId, UUID roomId) {
        MatchNotificationDto notification = new MatchNotificationDto(
                "MATCH_SUCCESS",
                roomId,
                "매칭이 성사됐어요! 대화를 시작해보세요.",
                LocalDateTime.now()
        );

        String key = key(memberId);
        try {
            String json = Ut.json.toString(notification);
            long score = Timestamp.valueOf(notification.getCreatedAt()).getTime();
            redisTemplate.opsForZSet().add(key, json, score);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.error("[MatchNotificationService] 알림 저장 실패 - memberId: {}", memberId, e);
        }
    }

    public List<MatchNotificationDto> getNotifications(UUID memberId, LocalDateTime after) {
        String key = key(memberId);
        List<MatchNotificationDto> notifications = new ArrayList<>();

        try {
            Set<String> jsonPayloads;
            if (after != null) {
                long minScore = Timestamp.valueOf(after).getTime();
                jsonPayloads = redisTemplate.opsForZSet().rangeByScore(key, minScore, Double.MAX_VALUE);
            } else {
                jsonPayloads = redisTemplate.opsForZSet().range(key, 0, -1);
            }

            if (jsonPayloads != null) {
                for (String json : jsonPayloads) {
                    notifications.add(Ut.json.objectMapper.readValue(json, MatchNotificationDto.class));
                }
            }
        } catch (Exception e) {
            log.error("[MatchNotificationService] 알림 조회 실패 - memberId: {}", memberId, e);
        }

        // 채팅 폴링과 동일하게, score 경계값(after와 정확히 같은 시각) 중복 수신 방지
        if (after != null) {
            notifications = notifications.stream()
                    .filter(n -> n.getCreatedAt().isAfter(after))
                    .toList();
        }

        return notifications;
    }

    private String key(UUID memberId) {
        return "notification:member:" + memberId;
    }
}