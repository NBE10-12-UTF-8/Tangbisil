package com.back.domain.trend.controller;

import com.back.domain.trend.dto.TrendKeywordResponseDto;
import com.back.domain.trend.mmr.dto.MmrConfig;
import com.back.domain.trend.ranking.dto.RankedKeywordDto;
import com.back.domain.trend.service.TrendKeywordService;
import com.back.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trend-keywords")
@RequiredArgsConstructor
@Tag(name = "ApiV1TrendKeywordController", description = "실시간 HOT 키워드 API")
public class ApiV1TrendKeywordController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int CANDIDATE_POOL_SIZE = 30;
    private static final int TOP_N = 10;
    private static final MmrConfig MMR_CONFIG = new MmrConfig(2.0, 0.9);
    private static final double TREND_FLAT_THRESHOLD = 0.5;

    private final TrendKeywordService trendKeywordService;

    @GetMapping
    @Operation(summary = "실시간 HOT 키워드 TOP 10 조회")
    public RsData<List<TrendKeywordResponseDto>> getTrendKeywords() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        List<RankedKeywordDto> ranked = trendKeywordService.getTrendingKeywords(yesterday, CANDIDATE_POOL_SIZE, TOP_N, MMR_CONFIG);
        List<TrendKeywordResponseDto> result = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            int rank = i + 1;
            String label = ranked.get(i).keyword();
            double zScore = ranked.get(i).zScore();

            String trend;
            if (zScore > TREND_FLAT_THRESHOLD) {
                trend = "up";
            } else if (zScore < -TREND_FLAT_THRESHOLD) {
                trend = "down";
            } else {
                trend = "flat";
            }

            result.add(new TrendKeywordResponseDto(rank, label, trend));
        }

        return new RsData<>("200-1", "조회 성공", result);
    }
}
