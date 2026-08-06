package com.back.domain.trend.service

import com.back.domain.trend.KeywordPairKey
import com.back.domain.trend.mmr.MmrDiversifier
import com.back.domain.trend.mmr.dto.MmrConfig
import com.back.domain.trend.npmi.NpmiCalculator
import com.back.domain.trend.npmi.dto.CooccurrenceStatsDto
import com.back.domain.trend.ranking.TrendKeywordRanker
import com.back.domain.trend.ranking.dto.RankedKeywordDto
import com.back.domain.trend.snapshot.repository.DailyCooccurrenceCountRepository
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class TrendKeywordService(
    private val trendKeywordRanker: TrendKeywordRanker,
    private val npmiCalculator: NpmiCalculator,
    private val mmrDiversifier: MmrDiversifier,
    private val dailyCooccurrenceCountRepository: DailyCooccurrenceCountRepository,
    private val dailyMessageCountRepository: DailyMessageCountRepository
) {
    fun getTrendingKeywords(targetDate: LocalDate, candidatePoolSize: Int, topN: Int, config: MmrConfig): List<RankedKeywordDto> {
        val candidates = trendKeywordRanker.rank(targetDate, candidatePoolSize)
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val totalMessages = dailyMessageCountRepository.findByDate(targetDate)?.totalMessages ?: 0L

        val candidateKeywords = candidates.map { it.keyword }
        val cooccurFrequencies = dailyCooccurrenceCountRepository
            .findAllByDateAndKeywordAInAndKeywordBIn(targetDate, candidateKeywords, candidateKeywords)
            .associate { KeywordPairKey.of(it.keywordA, it.keywordB) to it.frequency }

        val similarities = HashMap<String, Double>()
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                val candidateI = candidates[i]
                val candidateJ = candidates[j]

                val key = KeywordPairKey.of(candidateI.keyword, candidateJ.keyword)
                val freqXY = cooccurFrequencies.getOrDefault(key, 0L)

                val stats = CooccurrenceStatsDto(candidateI.frequency, candidateJ.frequency, freqXY, totalMessages)
                val npmi = npmiCalculator.calculate(stats)

                similarities[key] = npmi
            }
        }
        return mmrDiversifier.diversify(candidates, similarities, config, topN)
    }
}
