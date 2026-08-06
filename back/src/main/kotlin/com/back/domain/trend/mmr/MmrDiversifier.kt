package com.back.domain.trend.mmr

import com.back.domain.trend.KeywordPairKey
import com.back.domain.trend.mmr.dto.MmrConfig
import com.back.domain.trend.ranking.dto.RankedKeywordDto
import org.springframework.stereotype.Component

@Component
class MmrDiversifier {
    fun diversify(
        candidates: List<RankedKeywordDto>,
        similarities: Map<String, Double>,
        config: MmrConfig,
        topN: Int
    ): List<RankedKeywordDto> {
        if (candidates.isEmpty() || topN <= 0) {
            return emptyList()
        }
        val selected = ArrayList<RankedKeywordDto>()
        val remaining = ArrayList(candidates)
        remaining.sortWith(compareByDescending { it.zScore })
        val topCandidate = remaining[0]
        selected.add(topCandidate)
        remaining.remove(topCandidate)

        while (selected.size < topN && remaining.isNotEmpty()) {
            var best: RankedKeywordDto? = null
            var bestPenalizedScore = Double.NEGATIVE_INFINITY
            for (candidate in remaining) {
                val sim = maxSimilarityToSelected(candidate, selected, similarities)
                if (sim >= config.similarityThreshold) {
                    continue
                }
                val penalizedScore = candidate.zScore - config.alpha * sim
                if (best == null || penalizedScore > bestPenalizedScore) {
                    best = candidate
                    bestPenalizedScore = penalizedScore
                }
            }
            if (best == null) {
                break
            }
            selected.add(best)
            remaining.remove(best)
        }
        return selected
    }

    private fun maxSimilarityToSelected(
        candidate: RankedKeywordDto,
        selected: List<RankedKeywordDto>,
        similarities: Map<String, Double>
    ): Double {
        var maxSim = 0.0
        for (other in selected) {
            val sim = similarities.getOrDefault(KeywordPairKey.of(candidate.keyword, other.keyword), 0.0)
            if (sim > maxSim) {
                maxSim = sim
            }
        }
        return maxSim
    }
}
