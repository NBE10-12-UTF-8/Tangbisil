package com.back.domain.trend.snapshot.service;

import com.back.domain.trend.snapshot.repository.DailyCooccurrenceCountRepository;
import com.back.domain.trend.snapshot.repository.DailyKeywordCountRepository;
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "어제" 하루치 Redis 집계(ZSET/카운터)를 DailyKeywordCount/DailyMessageCount/
 * DailyCooccurrenceCount(MySQL)로 스냅샷하는 실제 로직.
 * TrendSnapshotScheduler에서 분리됨 - AOP self-invocation 문제로 @Transactional이
 * 별도 빈에 있어야 실제로 걸리기 때문 (작업 일지 참고).
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class TrendSnapshotServiceTest {

    @Autowired
    private TrendSnapshotService trendSnapshotService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DailyKeywordCountRepository dailyKeywordCountRepository;

    @Autowired
    private DailyMessageCountRepository dailyMessageCountRepository;

    @Autowired
    private DailyCooccurrenceCountRepository dailyCooccurrenceCountRepository;

    private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
    private static final String KEYWORD_KEY = "trend:keyword:" + YESTERDAY;
    private static final String MESSAGE_KEY = "trend:messages:" + YESTERDAY;
    private static final String COOCCUR_KEY = "trend:cooccur:" + YESTERDAY;

    @AfterEach
    void cleanUp() {
        // DB는 @Transactional로 각 테스트 후 롤백되지만, Redis는 트랜잭션 밖이라 직접 지운다.
        redisTemplate.delete(KEYWORD_KEY);
        redisTemplate.delete(MESSAGE_KEY);
        redisTemplate.delete(COOCCUR_KEY);
    }

    @Test
    @DisplayName("어제 ZSET에 쌓인 키워드마다 DailyKeywordCount 행이 하나씩 생긴다")
    void t1() {
        redisTemplate.opsForZSet().add(KEYWORD_KEY, "장마", 3);
        redisTemplate.opsForZSet().add(KEYWORD_KEY, "우산", 2);
        redisTemplate.opsForValue().set(MESSAGE_KEY, "10");

        trendSnapshotService.snapshotDate(YESTERDAY);

        assertThat(dailyKeywordCountRepository.findByDateAndKeyword(YESTERDAY, "장마"))
                .hasValueSatisfying(row -> assertThat(row.getFrequency()).isEqualTo(3));
        assertThat(dailyKeywordCountRepository.findByDateAndKeyword(YESTERDAY, "우산"))
                .hasValueSatisfying(row -> assertThat(row.getFrequency()).isEqualTo(2));
    }

    @Test
    @DisplayName("어제 메시지 카운터가 DailyMessageCount로 저장된다")
    void t2() {
        redisTemplate.opsForValue().set(MESSAGE_KEY, "7");

        trendSnapshotService.snapshotDate(YESTERDAY);

        assertThat(dailyMessageCountRepository.findByDate(YESTERDAY))
                .hasValueSatisfying(row -> assertThat(row.getTotalMessages()).isEqualTo(7));
    }

    @Test
    @DisplayName("어제 키워드 ZSET이 비어있어도(명사 없는 메시지만 있었음) 메시지 카운트는 그대로 저장된다")
    void t3() {
        redisTemplate.opsForValue().set(MESSAGE_KEY, "4");
        // KEYWORD_KEY는 의도적으로 채우지 않는다.

        trendSnapshotService.snapshotDate(YESTERDAY);

        assertThat(dailyKeywordCountRepository.findAll()).isEmpty();
        assertThat(dailyMessageCountRepository.findByDate(YESTERDAY))
                .hasValueSatisfying(row -> assertThat(row.getTotalMessages()).isEqualTo(4));
    }

    @Test
    @DisplayName("어제 메시지 카운터 키 자체가 없으면(그날 채팅이 아예 없었음) 아무것도 저장하지 않는다")
    void t4() {
        // KEYWORD_KEY, MESSAGE_KEY 둘 다 채우지 않는다.

        trendSnapshotService.snapshotDate(YESTERDAY);

        assertThat(dailyKeywordCountRepository.findAll()).isEmpty();
        assertThat(dailyMessageCountRepository.findByDate(YESTERDAY)).isEmpty();
    }

    @Test
    @DisplayName("같은 날짜에 스냅샷을 두 번 실행해도 행이 중복되지 않고 최신 값으로 갱신된다")
    void t5() {
        redisTemplate.opsForZSet().add(KEYWORD_KEY, "장마", 3);
        redisTemplate.opsForValue().set(MESSAGE_KEY, "5");
        trendSnapshotService.snapshotDate(YESTERDAY);

        redisTemplate.opsForZSet().incrementScore(KEYWORD_KEY, "장마", 2); // 누적 5로
        redisTemplate.opsForValue().set(MESSAGE_KEY, "8");
        trendSnapshotService.snapshotDate(YESTERDAY);

        assertThat(dailyKeywordCountRepository.findAll()).hasSize(1);
        assertThat(dailyKeywordCountRepository.findByDateAndKeyword(YESTERDAY, "장마"))
                .hasValueSatisfying(row -> assertThat(row.getFrequency()).isEqualTo(5));

        assertThat(dailyMessageCountRepository.findAll()).hasSize(1);
        assertThat(dailyMessageCountRepository.findByDate(YESTERDAY))
                .hasValueSatisfying(row -> assertThat(row.getTotalMessages()).isEqualTo(8));
    }

    @Test
    @DisplayName("어제 동시출현 ZSET에 쌓인 쌍마다 DailyCooccurrenceCount 행이 하나씩 생긴다")
    void t6() {
        redisTemplate.opsForZSet().add(COOCCUR_KEY, "장마::우산", 4);
        redisTemplate.opsForValue().set(MESSAGE_KEY, "10");

        trendSnapshotService.snapshotDate(YESTERDAY);

        assertThat(dailyCooccurrenceCountRepository.findByDateAndKeywordAAndKeywordB(YESTERDAY, "장마", "우산"))
                .hasValueSatisfying(row -> assertThat(row.getFrequency()).isEqualTo(4));
    }

    @Test
    @DisplayName("어제 동시출현 ZSET이 비어있어도(쌍이 없는 날) 나머지 스냅샷은 정상 처리된다")
    void t7() {
        redisTemplate.opsForValue().set(MESSAGE_KEY, "4");
        // COOCCUR_KEY는 의도적으로 채우지 않는다.

        trendSnapshotService.snapshotDate(YESTERDAY);

        assertThat(dailyCooccurrenceCountRepository.findAll()).isEmpty();
        assertThat(dailyMessageCountRepository.findByDate(YESTERDAY))
                .hasValueSatisfying(row -> assertThat(row.getTotalMessages()).isEqualTo(4));
    }

    @Test
    @DisplayName("같은 날짜에 스냅샷을 두 번 실행해도 동시출현 행이 중복되지 않고 최신 값으로 갱신된다")
    void t8() {
        redisTemplate.opsForZSet().add(COOCCUR_KEY, "장마::우산", 3);
        redisTemplate.opsForValue().set(MESSAGE_KEY, "5");
        trendSnapshotService.snapshotDate(YESTERDAY);

        redisTemplate.opsForZSet().incrementScore(COOCCUR_KEY, "장마::우산", 2); // 누적 5로
        redisTemplate.opsForValue().set(MESSAGE_KEY, "8");
        trendSnapshotService.snapshotDate(YESTERDAY);

        assertThat(dailyCooccurrenceCountRepository.findAll()).hasSize(1);
        assertThat(dailyCooccurrenceCountRepository.findByDateAndKeywordAAndKeywordB(YESTERDAY, "장마", "우산"))
                .hasValueSatisfying(row -> assertThat(row.getFrequency()).isEqualTo(5));
    }

    @Test
    @DisplayName("동시출현 ZSET에 구분자(::)가 없는 잘못된 형식의 멤버가 섞여 있어도, 그 건만 건너뛰고 나머지는 정상 처리되며 통째로 중단되지 않는다")
    void t9() {
        redisTemplate.opsForZSet().add(COOCCUR_KEY, "장마::우산", 3);
        redisTemplate.opsForZSet().add(COOCCUR_KEY, "형식이잘못된멤버", 1); // 구분자 없음
        redisTemplate.opsForValue().set(MESSAGE_KEY, "10");

        trendSnapshotService.snapshotDate(YESTERDAY);

        assertThat(dailyCooccurrenceCountRepository.findAll()).hasSize(1);
        assertThat(dailyCooccurrenceCountRepository.findByDateAndKeywordAAndKeywordB(YESTERDAY, "장마", "우산"))
                .hasValueSatisfying(row -> assertThat(row.getFrequency()).isEqualTo(3));
    }
}
