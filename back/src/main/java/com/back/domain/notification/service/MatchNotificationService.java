package com.back.domain.notification.service;

import com.back.domain.notification.dto.MatchNotificationDto;
import com.back.standard.util.Ut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
            long score = notification.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            redisTemplate.opsForZSet().add(key, json, score);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            // 알림 저장 실패가 매칭 성사 자체를 막으면 안 되므로 여기서는 로그만 남기고 삼킨다.
            log.error("[MatchNotificationService] 알림 저장 실패 - memberId: {}", memberId, e);
        }
    }

    public List<MatchNotificationDto> getNotifications(UUID memberId, LocalDateTime after) {
        String key = key(memberId);
        List<MatchNotificationDto> notifications = new ArrayList<>();

        // Redis 연결 실패는 여기서 그대로 전파된다 (장애 은폐 방지)
        Set<String> jsonPayloads;
        if (after != null) {
            long minScore = after.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            jsonPayloads = redisTemplate.opsForZSet().rangeByScore(key, minScore, Double.POSITIVE_INFINITY);
        } else {
            jsonPayloads = redisTemplate.opsForZSet().range(key, 0, -1);
        }

        if (jsonPayloads != null) {
            for (String json : jsonPayloads) {
                try {
                    notifications.add(Ut.json.objectMapper.readValue(json, MatchNotificationDto.class));
                } catch (Exception e) {
                    // 항목 하나가 손상됐다고 전체 조회를 실패시키지 않는다
                    log.error("[MatchNotificationService] 알림 역직렬화 실패 - payload: {}", json, e);
                }
            }
        }

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