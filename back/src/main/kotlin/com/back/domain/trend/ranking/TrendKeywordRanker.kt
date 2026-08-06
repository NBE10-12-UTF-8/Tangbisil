package com.back.domain.trend.ranking

import com.back.domain.trend.ranking.dto.RankedKeywordDto
import com.back.domain.trend.score.TrendZScoreCalculator
import com.back.domain.trend.score.dto.WordFrequencyStatsDto
import com.back.domain.trend.snapshot.repository.DailyKeywordCountRepository
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class TrendKeywordRanker(
    private val dailyKeywordCountRepository: DailyKeywordCountRepository,
    private val dailyMessageCountRepository: DailyMessageCountRepository,
    private val trendZScoreCalculator: TrendZScoreCalculator
) {
    fun rank(targetDate: LocalDate, topN: Int): List<RankedKeywordDto> {
        val messageCount = dailyMessageCountRepository.findByDate(targetDate) ?: return emptyList()

        val baselineDate = targetDate.minusDays(7)
        val baselineTotalMessages = dailyMessageCountRepository.findByDate(baselineDate)?.totalMessages ?: 0L

        val baselineFrequencies = dailyKeywordCountRepository.findAllByDate(baselineDate)
            .associate { it.keyword to it.frequency }

        val currentTotalMessages = messageCount.totalMessages

        val ranked = dailyKeywordCountRepository.findAllByDate(targetDate).map { dkc ->
            val baselineFrequency = baselineFrequencies.getOrDefault(dkc.keyword, 0L)
            val baseline = WordFrequencyStatsDto(baselineFrequency, baselineTotalMessages)
            val current = WordFrequencyStatsDto(dkc.frequency, currentTotalMessages)
            val zScore = trendZScoreCalculator.calculate(baseline, current)
            RankedKeywordDto(dkc.keyword, dkc.frequency, zScore)
        }.sortedByDescending { it.zScore }

        return ranked.subList(0, maxOf(0, minOf(topN, ranked.size)))
    }
}
