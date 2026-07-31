package com.back.domain.trend.aggregation;

import com.back.domain.chat.chatRoomMessage.event.ChatMessageSentEvent;
import com.back.domain.trend.keyword.NounExtractor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.List;

@Component
public class TrendAggregationEventHandler {
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
        List<String> nouns = nounExtractor.extract(content);

        LocalDate today = LocalDate.now();
        for(String noun : nouns){
            redisTemplate.opsForZSet().incrementScore("trend:keyword:" + today, noun, 1);
        }
        redisTemplate.opsForValue().increment("trend:messages:" + today);

    }

}
