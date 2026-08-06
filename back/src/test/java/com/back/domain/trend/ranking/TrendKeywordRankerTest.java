package com.back.domain.trend.ranking;

import com.back.domain.trend.ranking.dto.RankedKeywordDto;
import com.back.domain.trend.snapshot.entity.DailyKeywordCount;
import com.back.domain.trend.snapshot.entity.DailyMessageCount;
import com.back.domain.trend.snapshot.repository.DailyKeywordCountRepository;
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * DailyKeywordCount/DailyMessageCount(MySQL 스냅샷)에 저장된 값을 바탕으로,
 * targetDate 하루치 키워드들을 targetDate-7일(baseline)과 비교한 Z값 순으로 랭킹을 매긴다.
 * Redis는 전혀 관여하지 않는다 — 이미 스냅샷된 두 날짜의 DB 값만 갖고 계산하는 순수 조회+계산 로직.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class TrendKeywordRankerTest {

    @Autowired
    private TrendKeywordRanker trendKeywordRanker;

    @Autowired
    private DailyKeywordCountRepository dailyKeywordCountRepository;

    @Autowired
    private DailyMessageCountRepository dailyMessageCountRepository;

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 2);
    private static final LocalDate BASELINE_DATE = TARGET_DATE.minusDays(7);

    @Test
    @DisplayName("Z값이 높은 키워드 순으로 정렬되어 반환된다 (기준일에 없던 신규 키워드도 포함)")
    void rankedByZScoreDescending() {
        dailyMessageCountRepository.save(new DailyMessageCount(BASELINE_DATE, 1000));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "장마", 10));
        // "우산"은 baseline 날짜에 아예 없던 신규 키워드 — 행 자체를 저장하지 않는다.

        dailyMessageCountRepository.save(new DailyMessageCount(TARGET_DATE, 1000));
        dailyKeywordCountRepository.save(new DailyKeywordCount(TARGET_DATE, "장마", 50));
        dailyKeywordCountRepository.save(new DailyKeywordCount(TARGET_DATE, "우산", 20));

        List<RankedKeywordDto> result = trendKeywordRanker.rank(TARGET_DATE, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getKeyword()).isEqualTo("장마");
        assertThat(result.get(0).getFrequency()).isEqualTo(50);
        assertThat(result.get(0).getZScore()).isCloseTo(5.244, offset(0.01));

        assertThat(result.get(1).getKeyword()).isEqualTo("우산");
        assertThat(result.get(1).getFrequency()).isEqualTo(20);
        assertThat(result.get(1).getZScore()).isCloseTo(4.496, offset(0.01));
    }

    @Test
    @DisplayName("topN을 넘는 후보가 있으면 상위 N개까지만 반환된다")
    void limitedToTopN() {
        dailyMessageCountRepository.save(new DailyMessageCount(BASELINE_DATE, 1000));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "A", 10));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "B", 10));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "C", 10));

        dailyMessageCountRepository.save(new DailyMessageCount(TARGET_DATE, 1000));
        dailyKeywordCountRepository.save(new DailyKeywordCount(TARGET_DATE, "A", 100)); // 가장 큰 증가
        dailyKeywordCountRepository.save(new DailyKeywordCount(TARGET_DATE, "B", 50));  // 중간 증가
        dailyKeywordCountRepository.save(new DailyKeywordCount(TARGET_DATE, "C", 15));  // 가장 작은 증가

        List<RankedKeywordDto> result = trendKeywordRanker.rank(TARGET_DATE, 2);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RankedKeywordDto::getKeyword)
                .containsExactly("A", "B");
    }

    @Test
    @DisplayName("대상 날짜에 메시지 카운트 자체가 없으면 빈 리스트를 반환한다")
    void noMessageCountOnTargetDateReturnsEmpty() {
        // TARGET_DATE에 DailyMessageCount도, DailyKeywordCount도 아무것도 저장하지 않는다.

        List<RankedKeywordDto> result = trendKeywordRanker.rank(TARGET_DATE, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("기준일(targetDate-7일) 데이터 자체가 없어도 예외 없이 처리되고, 그 키워드의 Z값은 0으로 취급된다")
    void missingBaselineDayDoesNotThrow() {
        // BASELINE_DATE에는 DailyMessageCount 행 자체가 없다 (서비스 초기라 7일 전 데이터가 없는 상황을 가정).

        dailyMessageCountRepository.save(new DailyMessageCount(TARGET_DATE, 1000));
        dailyKeywordCountRepository.save(new DailyKeywordCount(TARGET_DATE, "장마", 50));

        List<RankedKeywordDto> result = trendKeywordRanker.rank(TARGET_DATE, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKeyword()).isEqualTo("장마");
        assertThat(result.get(0).getZScore()).isZero();
    }
}
