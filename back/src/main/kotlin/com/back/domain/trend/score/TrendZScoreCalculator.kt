package com.back.domain.trend.score

import com.back.domain.trend.score.dto.WordFrequencyStatsDto
import org.springframework.stereotype.Component
import kotlin.math.sqrt

@Component
class TrendZScoreCalculator {
    fun calculate(baseline: WordFrequencyStatsDto, current: WordFrequencyStatsDto): Double {
        if (baseline.totalMessages <= 0 || current.totalMessages <= 0) {
            return 0.0
        }
        val baselineRatio = calculateBaselineRatio(baseline)
        val currentRatio = calculateCurrentRatio(current)
        val pooledRatio = calculatePooledRatio(baseline, current)
        val standardError = calculateStandardError(pooledRatio, baseline, current)
        val zScore = (currentRatio - baselineRatio) / standardError
        if (zScore.isNaN() || zScore.isInfinite()) {
            return 0.0
        }
        return zScore
    }

    private fun calculateBaselineRatio(baseline: WordFrequencyStatsDto): Double =
        baseline.frequency.toDouble() / baseline.totalMessages

    private fun calculateCurrentRatio(current: WordFrequencyStatsDto): Double =
        current.frequency.toDouble() / current.totalMessages

    private fun calculatePooledRatio(baseline: WordFrequencyStatsDto, current: WordFrequencyStatsDto): Double =
        (baseline.frequency + current.frequency).toDouble() / (baseline.totalMessages + current.totalMessages)

    private fun calculateStandardError(
        pooledRatio: Double,
        baseline: WordFrequencyStatsDto,
        current: WordFrequencyStatsDto
    ): Double =
        sqrt(pooledRatio * (1 - pooledRatio) * (1.0 / current.totalMessages + 1.0 / baseline.totalMessages))
}
