package com.back.domain.notification.service

import com.back.domain.notification.dto.MatchNotificationDto
import com.back.standard.util.Ut
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class MatchNotificationService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(MatchNotificationService::class.java)

    companion object {
        private val TTL: Duration = Duration.ofDays(3)
        private const val NOTIFICATION_KEY_PREFIX = "notification:member:"
        private const val MATCH_SUCCESS_TYPE = "MATCH_SUCCESS"
    }

    fun notifyMatchSuccess(memberId: Long, roomId: UUID) {
        val notification = MatchNotificationDto(
            MATCH_SUCCESS_TYPE,
            roomId,
            "매칭이 성사됐어요! 대화를 시작해보세요.",
            LocalDateTime.now()
        )

        val key = key(memberId)
        try {
            val json = Ut.json.toString(notification)!!
            val score = notification.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            redisTemplate.opsForZSet().add(key, json, score.toDouble())

            // expire만으로는 활성 사용자의 오래된 항목이 안 지워지므로,
            // 새 알림 추가 시점에 TTL 창(3일) 밖의 오래된 항목을 함께 정리한다.
            val expiredBefore = LocalDateTime.now().minus(TTL)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            redisTemplate.opsForZSet().removeRangeByScore(key, 0.0, expiredBefore.toDouble())

            redisTemplate.expire(key, TTL)
        } catch (e: Exception) {
            // 알림 저장 실패가 매칭 성사 자체를 막으면 안 되므로 여기서는 로그만 남기고 삼킨다.
            log.error("[MatchNotificationService] 알림 저장 실패 - memberId: {}", memberId, e)
        }
    }

    // 조회 실패는 삼키지 않는다 - Redis 유일 저장소라 실패를 숨기면
    // "진짜 장애"와 "알림 없음"을 구분할 수 없어진다.
    fun getNotifications(memberId: Long, after: LocalDateTime?): List<MatchNotificationDto> {
        val key = key(memberId)
        val notifications = ArrayList<MatchNotificationDto>()

        // Redis 연결 실패는 여기서 그대로 전파된다 (장애 은폐 방지)
        val jsonPayloads: Set<String>? = if (after != null) {
            val minScore = after.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            redisTemplate.opsForZSet().rangeByScore(key, minScore.toDouble(), Double.POSITIVE_INFINITY)
        } else {
            redisTemplate.opsForZSet().range(key, 0, -1)
        }

        if (jsonPayloads != null) {
            for (json in jsonPayloads) {
                try {
                    notifications.add(Ut.json.objectMapper!!.readValue(json, MatchNotificationDto::class.java))
                } catch (e: Exception) {
                    // 항목 하나가 손상됐다고 전체 조회를 실패시키지 않는다
                    log.error("[MatchNotificationService] 알림 역직렬화 실패 - payload: {}", json, e)
                }
            }
        }

        // rangeByScore는 minScore를 포함(inclusive)해서 가져오므로,
        // after와 정확히 같은 시각의 알림(이미 클라이언트가 받아간 것)이 중복 전달된다.
        // 이를 제외하기 위해 엄격한 초과(isAfter) 조건으로 한 번 더 거른다. (채팅 폴링과 동일한 방식)
        return if (after != null) {
            notifications.filter { it.createdAt.isAfter(after) }
        } else {
            notifications
        }
    }

    private fun key(memberId: Long): String = NOTIFICATION_KEY_PREFIX + memberId
}
