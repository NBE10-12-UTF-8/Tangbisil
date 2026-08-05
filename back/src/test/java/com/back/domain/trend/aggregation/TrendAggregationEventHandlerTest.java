package com.back.domain.trend.aggregation;

import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;
import com.back.domain.chat.chatRoomMessage.event.ChatMessageSentEvent;
import com.back.domain.trend.KeywordPairKey;
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
import java.util.List;
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
    private static final String COOCCUR_KEY = "trend:cooccur:" + TODAY;
    private static final String FINGERPRINT_KEY = "trend:fingerprints:" + TODAY;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(KEYWORD_KEY);
        redisTemplate.delete(MESSAGE_KEY);
        redisTemplate.delete(COOCCUR_KEY);
        redisTemplate.delete(FINGERPRINT_KEY);
    }

    private ChatMessageSentEvent eventWithContent(String content) {
        return eventFrom(UUID.randomUUID(), content);
    }

    private ChatMessageSentEvent eventFrom(UUID senderMemberId, String content) {
        RedisChatMessageDto dto = new RedisChatMessageDto(
                UUID.randomUUID(), UUID.randomUUID(), "익명", senderMemberId, content, null);
        return new ChatMessageSentEvent(dto, List.of());
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

    @Test
    @DisplayName("한 메시지에 명사가 2개면, 그 쌍의 오늘 날짜 동시출현 ZSET 점수가 1 오른다")
    void t6() {
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent("장마 우산"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redisTemplate.opsForZSet().score(COOCCUR_KEY, KeywordPairKey.of("장마", "우산"))).isEqualTo(1.0));
    }

    @Test
    @DisplayName("한 메시지에 명사가 3개면, 만들 수 있는 3개 쌍 전부의 점수가 각각 1씩 오른다")
    void t7() {
        trendAggregationEventHandler.handleChatMessageSent(
                eventWithContent("오늘 장마 시작이래ㅋㅋㅋ 다들 우산 챙기세요"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(redisTemplate.opsForZSet().score(COOCCUR_KEY, KeywordPairKey.of("장마", "시작"))).isEqualTo(1.0);
            assertThat(redisTemplate.opsForZSet().score(COOCCUR_KEY, KeywordPairKey.of("장마", "우산"))).isEqualTo(1.0);
            assertThat(redisTemplate.opsForZSet().score(COOCCUR_KEY, KeywordPairKey.of("시작", "우산"))).isEqualTo(1.0);
        });
        assertThat(redisTemplate.opsForZSet().size(COOCCUR_KEY)).isEqualTo(3L);
    }

    @Test
    @DisplayName("같은 두 단어가 서로 다른 순서로 등장한 메시지 두 개를 처리하면, 하나의 쌍으로 점수가 누적된다")
    void t8() {
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent("장마 우산"));
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent("우산 장마"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redisTemplate.opsForZSet().score(COOCCUR_KEY, KeywordPairKey.of("장마", "우산"))).isEqualTo(2.0));
        assertThat(redisTemplate.opsForZSet().size(COOCCUR_KEY)).isEqualTo(1L);
    }

    @Test
    @DisplayName("명사가 1개 이하인 메시지는 쌍을 만들 수 없어 동시출현 ZSET에 아무것도 안 남는다")
    void t9() {
        trendAggregationEventHandler.handleChatMessageSent(eventWithContent("장마"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redisTemplate.opsForValue().get(MESSAGE_KEY)).isEqualTo("1"));
        assertThat(redisTemplate.opsForZSet().size(COOCCUR_KEY)).isIn(0L, null);
    }

    @Test
    @DisplayName("같은 발신자가 완전히 같은 메시지를 반복하면(도배), 두 번째부터는 전체 메시지 수·키워드 집계 어디에도 반영되지 않는다")
    void t10() {
        UUID sender = UUID.randomUUID();

        trendAggregationEventHandler.handleChatMessageSent(eventFrom(sender, "장마 진짜 심하다 다들 우산 챙기세요"));
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(redisTemplate.opsForValue().get(MESSAGE_KEY)).isEqualTo("1"));

        trendAggregationEventHandler.handleChatMessageSent(eventFrom(sender, "장마 진짜 심하다 다들 우산 챙기세요"));
        trendAggregationEventHandler.handleChatMessageSent(eventFrom(sender, "장마 진짜 심하다 다들 우산 챙기세요"));

        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get(MESSAGE_KEY)).isEqualTo("1");
            assertThat(redisTemplate.opsForZSet().score(KEYWORD_KEY, "장마")).isEqualTo(1.0);
        });
    }

    @Test
    @DisplayName("서로 다른 발신자가 같은 메시지를 보내면 도배로 보지 않고 둘 다 집계에 반영한다 — 여러 사람이 같은 말을 하는 건 실제 트렌드 신호다")
    void t11() {
        trendAggregationEventHandler.handleChatMessageSent(eventFrom(UUID.randomUUID(), "장마 진짜 심하다"));
        trendAggregationEventHandler.handleChatMessageSent(eventFrom(UUID.randomUUID(), "장마 진짜 심하다"));

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(redisTemplate.opsForValue().get(MESSAGE_KEY)).isEqualTo("2");
            assertThat(redisTemplate.opsForZSet().score(KEYWORD_KEY, "장마")).isEqualTo(2.0);
        });
    }
}
