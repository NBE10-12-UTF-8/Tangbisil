package com.back.domain.chat.chatRoomMessage.event

import com.back.domain.chat.chatRoomMessage.dto.BroadcastChatMessageDto
import com.back.standard.util.Ut
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.sql.Timestamp
import java.time.Duration

@Component
class ChatMessageEventHandler(
    private val redisTemplate: RedisTemplate<String, String>,
    private val messagingTemplate: SimpMessagingTemplate
) {
    companion object {
        private val log = LoggerFactory.getLogger(ChatMessageEventHandler::class.java)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleChatMessageSent(event: ChatMessageSentEvent) {
        val dto = event.messageDto
        val key = "chat:room:${dto.roomId}:messages"

        try {
            val jsonPayload = Ut.json.toString(dto)!!
            val score = Timestamp.valueOf(dto.createdAt).time

            redisTemplate.opsForZSet().add(key, jsonPayload, score.toDouble())
            redisTemplate.expire(key, Duration.ofHours(2))
        } catch (e: Exception) {
            log.error("Redis ZSet 캐시 적재 실패! 데이터 일관성을 지키기 위해 캐시를 무효화(DEL)합니다. Key: {}", key, e)
            try {
                redisTemplate.delete(key)
            } catch (deleteEx: Exception) {
                log.error("Redis 캐시 무효화 실패 - Key: {}", key, deleteEx)
            }
        }

        for (target in event.targets) {
            if (target.isBot) continue
            try {
                val isMine = target.participantId == dto.senderParticipantId
                messagingTemplate.convertAndSendToUser(
                    target.memberId,
                    "/queue/rooms/${dto.roomId}",
                    BroadcastChatMessageDto(dto, isMine)
                )
            } catch (e: Exception) {
                log.error("WebSocket 브로드캐스트 실패 - roomId: {}, memberId: {}", dto.roomId, target.memberId, e)
            }
        }
    }
}
