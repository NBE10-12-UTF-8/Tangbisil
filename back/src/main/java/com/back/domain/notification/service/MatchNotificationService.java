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

    // 조회 실패는 삼키지 않는다 - Redis 유일 저장소라 실패를 숨기면
    // "진짜 장애"와 "알림 없음"을 구분할 수 없어진다.
    public List<MatchNotificationDto> getNotifications(UUID memberId, LocalDateTime after) {
        String key = key(memberId);
        List<MatchNotificationDto> notifications = new ArrayList<>();

        Set<String> jsonPayloads;
        if (after != null) {
            long minScore = after.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            jsonPayloads = redisTemplate.opsForZSet().rangeByScore(key, minScore, Double.MAX_VALUE);
        } else {
            jsonPayloads = redisTemplate.opsForZSet().range(key, 0, -1);
        }

        if (jsonPayloads != null) {
            for (String json : jsonPayloads) {
                notifications.add(Ut.json.objectMapper.readValue(json, MatchNotificationDto.class));
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