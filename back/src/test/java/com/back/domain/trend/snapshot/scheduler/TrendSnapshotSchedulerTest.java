package com.back.domain.trend.snapshot.scheduler;

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
 * 매일 00:05(KST)에 도는 것을 전제로, "어제" 하루치 Redis 집계(ZSET/카운터)를
 * DailyKeywordCount/DailyMessageCount(MySQL)로 스냅샷하는 배치 잡.
 * cron 트리거 없이 스냅샷 메서드를 직접 호출해서 검증한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class TrendSnapshotSchedulerTest {

    @Autowired
    private TrendSnapshotScheduler trendSnapshotScheduler;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DailyKeywordCountRepository dailyKeywordCountRepository;

    @Autowired
    private DailyMessageCountRepository dailyMessageCountRepository;

    private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
    private static final String KEYWORD_KEY = "trend:keyword:" + YESTERDAY;
    private static final String MESSAGE_KEY = "trend:messages:" + YESTERDAY;

    @AfterEach
    void cleanUp() {
        // DB는 @Transactional로 각 테스트 후 롤백되지만, Redis는 트랜잭션 밖이라 직접 지운다.
        redisTemplate.delete(KEYWORD_KEY);
        redisTemplate.delete(MESSAGE_KEY);
    }

    @Test
    @DisplayName("어제 ZSET에 쌓인 키워드마다 DailyKeywordCount 행이 하나씩 생긴다")
    void t1() {
        redisTemplate.opsForZSet().add(KEYWORD_KEY, "장마", 3);
        redisTemplate.opsForZSet().add(KEYWORD_KEY, "우산", 2);
        redisTemplate.opsForValue().set(MESSAGE_KEY, "10");

        trendSnapshotScheduler.snapshotYesterday();

        assertThat(dailyKeywordCountRepository.findByDateAndKeyword(YESTERDAY, "장마"))
                .hasValueSatisfying(row -> assertThat(row.getFrequency()).isEqualTo(3));
        assertThat(dailyKeywordCountRepository.findByDateAndKeyword(YESTERDAY, "우산"))
                .hasValueSatisfying(row -> assertThat(row.getFrequency()).isEqualTo(2));
    }

    @Test
    @DisplayName("어제 메시지 카운터가 DailyMessageCount로 저장된다")
    void t2() {
        redisTemplate.opsForValue().set(MESSAGE_KEY, "7");

        trendSnapshotScheduler.snapshotYesterday();

        assertThat(dailyMessageCountRepository.findByDate(YESTERDAY))
                .hasValueSatisfying(row -> assertThat(row.getTotalMessages()).isEqualTo(7));
    }

    @Test
    @DisplayName("어제 키워드 ZSET이 비어있어도(명사 없는 메시지만 있었음) 메시지 카운트는 그대로 저장된다")
    void t3() {
        redisTemplate.opsForValue().set(MESSAGE_KEY, "4");
        // KEYWORD_KEY는 의도적으로 채우지 않는다.

        trendSnapshotScheduler.snapshotYesterday();

        assertThat(dailyKeywordCountRepository.findAll()).isEmpty();
        assertThat(dailyMessageCountRepository.findByDate(YESTERDAY))
                .hasValueSatisfying(row -> assertThat(row.getTotalMessages()).isEqualTo(4));
    }

    @Test
    @DisplayName("어제 메시지 카운터 키 자체가 없으면(그날 채팅이 아예 없었음) 아무것도 저장하지 않는다")
    void t4() {
        // KEYWORD_KEY, MESSAGE_KEY 둘 다 채우지 않는다.

        trendSnapshotScheduler.snapshotYesterday();

        assertThat(dailyKeywordCountRepository.findAll()).isEmpty();
        assertThat(dailyMessageCountRepository.findByDate(YESTERDAY)).isEmpty();
    }

    @Test
    @DisplayName("같은 날짜에 스냅샷을 두 번 실행해도 행이 중복되지 않고 최신 값으로 갱신된다")
    void t5() {
        redisTemplate.opsForZSet().add(KEYWORD_KEY, "장마", 3);
        redisTemplate.opsForValue().set(MESSAGE_KEY, "5");
        trendSnapshotScheduler.snapshotYesterday();

        redisTemplate.opsForZSet().incrementScore(KEYWORD_KEY, "장마", 2); // 누적 5로
        redisTemplate.opsForValue().set(MESSAGE_KEY, "8");
        trendSnapshotScheduler.snapshotYesterday();

        assertThat(dailyKeywordCountRepository.findAll()).hasSize(1);
        assertThat(dailyKeywordCountRepository.findByDateAndKeyword(YESTERDAY, "장마"))
                .hasValueSatisfying(row -> assertThat(row.getFrequency()).isEqualTo(5));

        assertThat(dailyMessageCountRepository.findAll()).hasSize(1);
        assertThat(dailyMessageCountRepository.findByDate(YESTERDAY))
                .hasValueSatisfying(row -> assertThat(row.getTotalMessages()).isEqualTo(8));
    }
}
