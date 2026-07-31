package com.back.domain.trend.aggregation;

import com.back.domain.chat.chatRoomMessage.event.ChatMessageSentEvent;
import com.back.domain.trend.keyword.NounExtractor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class TrendAggregationEventHandler {

    // MySQL 스냅샷 스케줄러가 아직 없어 정확한 보존 기간이 확정되지 않았으므로,
    // 무한 누적을 막는 안전망 용도로 넉넉하게 잡는다. 스케줄러 도입 시 재검토.
    private static final Duration KEY_TTL = Duration.ofDays(7);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NounExtractor nounExtractor;
    private final RedisTemplate<String, String> redisTemplate;

    public TrendAggregationEventHandler(RedisTemplate<String, String> redisTemplate , NounExtractor nounExtractor) {
        this.redisTemplate = redisTemplate;
        this.nounExtractor =nounExtractor;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT )
    public void handleChatMessageSent (ChatMessageSentEvent event){

        String content = event.getMessageDto() != null ? event.getMessageDto().getContent() : null;
        List<String> nouns = nounExtractor.extract(content).stream().distinct().toList();

        LocalDate today = LocalDate.now(KST);
        String keywordKey = "trend:keyword:" + today;
        String messageKey = "trend:messages:" + today;

        for(String noun : nouns){
            redisTemplate.opsForZSet().incrementScore(keywordKey, noun, 1);
        }
        redisTemplate.opsForValue().increment(messageKey);

        if (!nouns.isEmpty()) {
            redisTemplate.expire(keywordKey, KEY_TTL);
        }
        redisTemplate.expire(messageKey, KEY_TTL);

    }

}
