package com.back.domain.trend.service;

import com.back.domain.trend.mmr.dto.MmrConfig;
import com.back.domain.trend.ranking.dto.RankedKeywordDto;
import com.back.domain.trend.snapshot.entity.DailyCooccurrenceCount;
import com.back.domain.trend.snapshot.entity.DailyKeywordCount;
import com.back.domain.trend.snapshot.entity.DailyMessageCount;
import com.back.domain.trend.snapshot.repository.DailyCooccurrenceCountRepository;
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

/**
 * TrendKeywordRanker(Z-테스트 랭킹) + NpmiCalculator(유사도) + MmrDiversifier(다양화)를
 * 실제 스냅샷 데이터로 연결해 "오늘의 트렌드 키워드" 최종 리스트를 만드는 조립부.
 * Redis는 관여하지 않는다 — 전부 이미 스냅샷된 MySQL 데이터만으로 계산한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class TrendKeywordServiceTest {

    @Autowired
    private TrendKeywordService trendKeywordService;

    @Autowired
    private DailyKeywordCountRepository dailyKeywordCountRepository;

    @Autowired
    private DailyMessageCountRepository dailyMessageCountRepository;

    @Autowired
    private DailyCooccurrenceCountRepository dailyCooccurrenceCountRepository;

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 2);
    private static final LocalDate BASELINE_DATE = TARGET_DATE.minusDays(7);

    @Test
    @DisplayName("Z-테스트로 뽑은 원점수 순위가 높아도, 이미 뽑힌 키워드와 NPMI 유사도가 높으면 MMR로 순위가 밀려난다")
    void mmrReordersBasedOnRealNpmiSimilarity() {
        // 기준일: 셋 다 baseline 빈도 동일 -> 오늘 증가폭 차이로만 원점수 순위가 갈리게 한다
        dailyMessageCountRepository.save(new DailyMessageCount(BASELINE_DATE, 1000));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "장마", 5));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "태풍", 5));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "우산", 5));

        // 오늘: 원점수는 장마 > 태풍 > 우산 순으로, 세 값이 서로 근접하게 잡는다
        dailyMessageCountRepository.save(new DailyMessageCount(TARGET_DATE, 1000));
        dailyKeywordCountRepository.save(new DailyKeywordCount(TARGET_DATE, "장마", 50));
        dailyKeywordCountRepository.save(new DailyKeywordCount(TARGET_DATE, "태풍", 45));
        dailyKeywordCountRepository.save(new DailyKeywordCount(TARGET_DATE, "우산", 40));

        // 동시출현: 장마-태풍은 강하게 겹치고(NPMI 높음), 나머지 쌍은 거의 안 겹친다(NPMI 낮음)
        dailyCooccurrenceCountRepository.save(new DailyCooccurrenceCount(TARGET_DATE, "장마", "태풍", 40));
        dailyCooccurrenceCountRepository.save(new DailyCooccurrenceCount(TARGET_DATE, "장마", "우산", 2));
        dailyCooccurrenceCountRepository.save(new DailyCooccurrenceCount(TARGET_DATE, "태풍", "우산", 2));

        MmrConfig config = new MmrConfig(2.0, 0.9);
        List<RankedKeywordDto> result = trendKeywordService.getTrendingKeywords(TARGET_DATE, 10, 3, config);

        // 원점수만 보면 장마>태풍>우산이지만, 장마-태풍의 NPMI가 높아 태풍이 감점되어
        // 장마-우산의 낮은 NPMI 덕에 상대적으로 덜 깎인 우산이 태풍보다 먼저 나온다.
        assertThat(result).extracting(RankedKeywordDto::getKeyword)
                .containsExactly("장마", "우산", "태풍");
    }

    @Test
    @DisplayName("대상 날짜에 데이터가 없으면 빈 리스트를 반환한다")
    void noDataReturnsEmptyList() {
        List<RankedKeywordDto> result = trendKeywordService.getTrendingKeywords(TARGET_DATE, 10, 3, new MmrConfig(2.0, 0.9));

        assertThat(result).isEmpty();
    }
}
