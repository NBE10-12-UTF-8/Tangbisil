package com.back.domain.chat.chatRoomMessage.event;

import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;
import com.back.standard.util.Ut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

@Component
public class ChatMessageEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageEventHandler.class);

    private final RedisTemplate<String, String> redisTemplate;

    public ChatMessageEventHandler(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChatMessageSent(ChatMessageSentEvent event) {
        RedisChatMessageDto dto = event.getMessageDto();
        String key = "chat:room:" + dto.getRoomId() + ":messages";

        try {
            // StringRedisSerializer에 맞춰 수동 직렬화(JSON String)
            String jsonPayload = Ut.json.toString(dto);
            long score = java.sql.Timestamp.valueOf(dto.getCreatedAt()).getTime();

            redisTemplate.opsForZSet().add(key, jsonPayload, score);

            // Active 방 누수 방지용 Safety TTL (2시간) 지정
            redisTemplate.expire(key, Duration.ofHours(2));

        } catch (Exception e) {
            log.error("Redis ZSet 캐시 적재 실패! 데이터 일관성을 지키기 위해 캐시를 무효화(DEL)합니다. Key: {}", key, e);
            try {
                // 자가 치유(Self-Healing) 캐시 삭제
                redisTemplate.delete(key);
            } catch (Exception deleteEx) {
                log.error("Redis 캐시 무효화 실패 - Key: {}", key, deleteEx);
            }
        }
    }
}