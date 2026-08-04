package com.back.domain.trend.controller;

import com.back.domain.trend.snapshot.entity.DailyKeywordCount;
import com.back.domain.trend.snapshot.entity.DailyMessageCount;
import com.back.domain.trend.snapshot.repository.DailyKeywordCountRepository;
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 홈 화면 "실시간 HOT 키워드" 위젯이 붙을 공개 엔드포인트.
 * GET /api/v1/trend-keywords -> {rank, label, trend}[] (trend: up/down/flat)
 * 대상 날짜는 스냅샷이 완전히 끝난 "어제"(KST) 고정.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiV1TrendKeywordControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DailyKeywordCountRepository dailyKeywordCountRepository;

    @Autowired
    private DailyMessageCountRepository dailyMessageCountRepository;

    private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
    private static final LocalDate BASELINE_DATE = YESTERDAY.minusDays(7);

    @Test
    @DisplayName("순위·라벨과 zScore 기반 trend(up/down/flat)를 담아 응답한다")
    void returnsRankedKeywordsWithTrend() throws Exception {
        dailyMessageCountRepository.save(new DailyMessageCount(BASELINE_DATE, 1000));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "장마", 20));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "태풍", 20));
        dailyKeywordCountRepository.save(new DailyKeywordCount(BASELINE_DATE, "우산", 20));

        dailyMessageCountRepository.save(new DailyMessageCount(YESTERDAY, 1000));
        dailyKeywordCountRepository.save(new DailyKeywordCount(YESTERDAY, "장마", 40)); // zScore ~ +2.6 -> up
        dailyKeywordCountRepository.save(new DailyKeywordCount(YESTERDAY, "태풍", 20)); // zScore = 0 -> flat
        dailyKeywordCountRepository.save(new DailyKeywordCount(YESTERDAY, "우산", 5));  // zScore ~ -3.0 -> down
        // 동시출현 데이터는 의도적으로 비워둔다 - 전부 없으면 MMR 보정이 후보 전체에 균등하게
        // 적용돼 원래 zScore 순서가 그대로 유지된다 (candidatePoolSize/topN 로직만 검증하기 위함).

        mvc.perform(get("/api/v1/trend-keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].label").value("장마"))
                .andExpect(jsonPath("$.data[0].trend").value("up"))
                .andExpect(jsonPath("$.data[1].rank").value(2))
                .andExpect(jsonPath("$.data[1].label").value("태풍"))
                .andExpect(jsonPath("$.data[1].trend").value("flat"))
                .andExpect(jsonPath("$.data[2].rank").value(3))
                .andExpect(jsonPath("$.data[2].label").value("우산"))
                .andExpect(jsonPath("$.data[2].trend").value("down"));
    }

    @Test
    @DisplayName("어제 데이터가 없으면 빈 배열을 200으로 응답한다")
    void noDataReturnsEmptyArray() throws Exception {
        mvc.perform(get("/api/v1/trend-keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
