package com.back.domain.trend.npmi

import com.back.domain.trend.npmi.dto.CooccurrenceStatsDto
import org.springframework.stereotype.Component
import kotlin.math.ln

@Component
class NpmiCalculator {
    companion object {
        private val LOG_2 = ln(2.0)
    }

    fun calculate(stats: CooccurrenceStatsDto): Double {
        if (stats.totalMessages <= 0) {
            return 0.0
        }
        if (stats.freqXY == 0L) {
            return -1.0
        }
        if (stats.freqXY == stats.totalMessages) {
            return 1.0
        }

        val pX = calculateProbability(stats.freqX, stats.totalMessages)
        val pY = calculateProbability(stats.freqY, stats.totalMessages)
        val pXY = calculateProbability(stats.freqXY, stats.totalMessages)
        val pmi = calculatePmi(pXY, pX, pY)
        return -pmi / (ln(pXY) / LOG_2)
    }

    private fun calculateProbability(freq: Long, totalMessages: Long): Double = freq.toDouble() / totalMessages

    private fun calculatePmi(pXY: Double, pX: Double, pY: Double): Double = ln(pXY / (pX * pY)) / LOG_2
}
