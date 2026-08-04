package com.back.domain.trend.aggregation;

import com.back.domain.chat.chatRoomMessage.event.ChatMessageSentEvent;
import com.back.domain.trend.KeywordPairKey;
import com.back.domain.trend.dedup.MessageDuplicateChecker;
import com.back.domain.trend.keyword.NounExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TrendAggregationEventHandler.class);

    // MySQL 스냅샷 스케줄러가 아직 없어 정확한 보존 기간이 확정되지 않았으므로,
    // 무한 누적을 막는 안전망 용도로 넉넉하게 잡는다. 스케줄러 도입 시 재검토.
    private static final Duration KEY_TTL = Duration.ofDays(7);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NounExtractor nounExtractor;
    private final MessageDuplicateChecker messageDuplicateChecker;
    private final RedisTemplate<String, String> redisTemplate;

    public TrendAggregationEventHandler(RedisTemplate<String, String> redisTemplate,
                                         NounExtractor nounExtractor,
                                         MessageDuplicateChecker messageDuplicateChecker) {
        this.redisTemplate = redisTemplate;
        this.nounExtractor = nounExtractor;
        this.messageDuplicateChecker = messageDuplicateChecker;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChatMessageSent(ChatMessageSentEvent event) {

        String content = event.getMessageDto() != null ? event.getMessageDto().getContent() : null;
        LocalDate today = LocalDate.now(KST);

        if (messageDuplicateChecker.isDuplicate(today, content)) {
            return;
        }

        List<String> nouns = nounExtractor.extract(content).stream().distinct().toList();

        String keywordKey = "trend:keyword:" + today;
        String messageKey = "trend:messages:" + today;
        String cooccurKey = "trend:cooccur:" + today;

        try {
            for (String noun : nouns) {
                redisTemplate.opsForZSet().incrementScore(keywordKey, noun, 1);
            }
            for (int i = 0; i < nouns.size(); i++) {
                for (int j = i + 1; j < nouns.size(); j++) {
                    redisTemplate.opsForZSet().incrementScore(cooccurKey, KeywordPairKey.of(nouns.get(i), nouns.get(j)), 1);
                }
            }
            redisTemplate.opsForValue().increment(messageKey);

            if (!nouns.isEmpty()) {
                redisTemplate.expire(keywordKey, KEY_TTL);
            }
            if (nouns.size() >= 2) {
                redisTemplate.expire(cooccurKey, KEY_TTL);
            }
            redisTemplate.expire(messageKey, KEY_TTL);
        } catch (Exception e) {
            // 이미 채팅 메시지는 COMMIT된 뒤라 여기서 실패해도 재시도 대상이 아니다.
            // @Async라 예외가 기본 핸들러에 조용히 삼켜지므로, 트렌드 집계 누락을 알아챌 수 있도록 로그만 남긴다.
            log.error("트렌드 키워드 집계 실패 - keywordKey={}, messageKey={}", keywordKey, messageKey, e);
        }
    }
}
