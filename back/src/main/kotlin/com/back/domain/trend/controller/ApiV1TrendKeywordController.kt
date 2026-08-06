package com.back.domain.trend.controller

import com.back.domain.trend.dto.TrendKeywordResponseDto
import com.back.domain.trend.mmr.dto.MmrConfig
import com.back.domain.trend.service.TrendKeywordService
import com.back.global.rsData.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/api/v1/trend-keywords")
@Tag(name = "ApiV1TrendKeywordController", description = "실시간 HOT 키워드 API")
class ApiV1TrendKeywordController(
    private val trendKeywordService: TrendKeywordService
) {
    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private const val CANDIDATE_POOL_SIZE = 30
        private const val TOP_N = 10
        private val MMR_CONFIG = MmrConfig(2.0, 0.9)
        private const val TREND_FLAT_THRESHOLD = 0.5
    }

    @GetMapping
    @Operation(summary = "실시간 HOT 키워드 TOP 10 조회")
    fun getTrendKeywords(): RsData<List<TrendKeywordResponseDto>> {
        val yesterday = LocalDate.now(KST).minusDays(1)
        val ranked = trendKeywordService.getTrendingKeywords(yesterday, CANDIDATE_POOL_SIZE, TOP_N, MMR_CONFIG)

        val result = ranked.mapIndexed { i, dto ->
            TrendKeywordResponseDto(i + 1, dto.keyword, determineTrend(dto.zScore))
        }

        return RsData("200-1", "조회 성공", result)
    }

    private fun determineTrend(zScore: Double): String {
        if (zScore > TREND_FLAT_THRESHOLD) {
            return "up"
        }
        if (zScore < -TREND_FLAT_THRESHOLD) {
            return "down"
        }
        return "flat"
    }
}
