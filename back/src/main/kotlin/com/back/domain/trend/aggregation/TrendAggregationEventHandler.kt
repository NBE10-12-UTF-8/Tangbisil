package com.back.domain.trend.aggregation

import com.back.domain.chat.chatRoomMessage.event.ChatMessageSentEvent
import com.back.domain.trend.KeywordPairKey
import com.back.domain.trend.dedup.MessageDuplicateChecker
import com.back.domain.trend.keyword.NounExtractor
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

@Component
class TrendAggregationEventHandler(
    private val redisTemplate: RedisTemplate<String, String>,
    private val nounExtractor: NounExtractor,
    private val messageDuplicateChecker: MessageDuplicateChecker
) {
    companion object {
        private val log = LoggerFactory.getLogger(TrendAggregationEventHandler::class.java)

        // MySQL 스냅샷 스케줄러가 아직 없어 정확한 보존 기간이 확정되지 않았으므로,
        // 무한 누적을 막는 안전망 용도로 넉넉하게 잡는다. 스케줄러 도입 시 재검토.
        private val KEY_TTL: Duration = Duration.ofDays(7)
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleChatMessageSent(event: ChatMessageSentEvent) {
        val content = event.messageDto.content
        val senderParticipantId = event.messageDto.senderParticipantId
        val today = LocalDate.now(KST)

        if (messageDuplicateChecker.isDuplicate(today, senderParticipantId, content)) {
            return
        }

        val nouns = nounExtractor.extract(content).distinct()

        val keywordKey = "trend:keyword:$today"
        val messageKey = "trend:messages:$today"
        val cooccurKey = "trend:cooccur:$today"

        try {
            for (noun in nouns) {
                redisTemplate.opsForZSet().incrementScore(keywordKey, noun, 1.0)
            }
            for (i in nouns.indices) {
                for (j in i + 1 until nouns.size) {
                    redisTemplate.opsForZSet().incrementScore(cooccurKey, KeywordPairKey.of(nouns[i], nouns[j]), 1.0)
                }
            }
            redisTemplate.opsForValue().increment(messageKey)

            if (nouns.isNotEmpty()) {
                redisTemplate.expire(keywordKey, KEY_TTL)
            }
            if (nouns.size >= 2) {
                redisTemplate.expire(cooccurKey, KEY_TTL)
            }
            redisTemplate.expire(messageKey, KEY_TTL)
        } catch (e: Exception) {
            // 이미 채팅 메시지는 COMMIT된 뒤라 여기서 실패해도 재시도 대상이 아니다.
            // @Async라 예외가 기본 핸들러에 조용히 삼켜지므로, 트렌드 집계 누락을 알아챌 수 있도록 로그만 남긴다.
            log.error("트렌드 키워드 집계 실패 - keywordKey={}, messageKey={}", keywordKey, messageKey, e)
        }
    }
}
