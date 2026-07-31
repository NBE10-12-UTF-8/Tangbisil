package com.back.domain.trend.aggregation;

import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;
import com.back.domain.chat.chatRoomMessage.event.ChatMessageSentEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 옵션 B(레디스 우선 카운팅) 첫 단계 - ChatMessageSentEvent를 받아 명사를 추출하고
 * 그 결과를 Redis에 실시간으로 반영하는 핸들러. MySQL 스냅샷은 이후 별도 스케줄러에서 다룬다.
 */
@ActiveProfiles("test")
@SpringBootTest
class TrendAggregationEventHandlerTest {

    @Autowired
    private TrendAggregationEventHandler trendAggregationEventHandler;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private static final String KEYWORD_KEY = "trend:keyword:" + TODAY;
    private static final String MESSAGE_KEY = "trend:messages:" + TODAY;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(KEYWORD_KEY);
        redisTemplate.delete(MESSAGE_KEY);
    }

    private ChatMessageSentEvent eventWithContent(String content) {
        RedisChatMessageDto dto = new RedisChatMessageDto(
                UUID.randomUUID(), UUID.randomUUID(), "익명", UUID.randomUUID(), content, null);
        return new ChatMessageSentEvent(dto);
    }

    @Test
    @DisplayName("메시지 하나를 처리하면 추출된 명사마다 오늘 날짜 ZSET 점수가 1씩 올라간다")
    void t1() {
        trendAggregationEventHandler.handleChatMessageSent(
                eventWithContent("오늘 장마 시작이래ㅋㅋㅋ 다들 우산 챙기세요"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(redisTemplate.opsForZSet().score(KEYWORD_KEY, "장마")).isEqualTo(1.0);
            assertThat(redisTemplate.opsForZSet().score(KEYWORD_KEY, "우산")).isEqualTo(1.0);
        });
    }

    @Test
    @DisplayName("같은 명사가 여러 메시지에 등장하면 ZSET 점수가 누적된다")
    void t2() {
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent("장마 진짜 심하다"));
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent("장마 언제 끝나나"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redisTemplate.opsForZSet().score(KEYWORD_KEY, "장마")).isEqualTo(2.0));
    }

    @Test
    @DisplayName("메시지를 처리할 때마다 오늘 날짜 전체 메시지 카운터도 1씩 증가한다")
    void t3() {
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent("첫 메시지"));
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent("두 번째 메시지"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redisTemplate.opsForValue().get(MESSAGE_KEY)).isEqualTo("2"));
    }

    @Test
    @DisplayName("명사가 하나도 없는 메시지(예: 이모티콘/감탄사뿐)는 키워드 ZSET에 아무것도 안 남지만, 전체 메시지 카운트는 그대로 증가한다")
    void t4() {
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent("ㅋㅋㅋㅋㅋ"));

        // 카운터가 "1"이 될 때까지 기다려서 비동기 처리가 끝났음을 먼저 확인한 뒤,
        // 그 시점에 키워드 ZSET은 여전히 비어있는지 확인한다.
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redisTemplate.opsForValue().get(MESSAGE_KEY)).isEqualTo("1"));
        assertThat(redisTemplate.opsForZSet().size(KEYWORD_KEY)).isIn(0L, null);
    }

    @Test
    @DisplayName("메시지 content가 null이어도 예외 없이 처리되고, 명사만 없을 뿐 전체 메시지 카운트는 증가한다")
    void t5() {
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent(null));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redisTemplate.opsForValue().get(MESSAGE_KEY)).isEqualTo("1"));
        assertThat(redisTemplate.opsForZSet().size(KEYWORD_KEY)).isIn(0L, null);
    }
}
