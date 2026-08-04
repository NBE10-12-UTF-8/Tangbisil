package com.back.domain.trend.mmr;

import com.back.domain.trend.KeywordPairKey;
import com.back.domain.trend.mmr.dto.MmrConfig;
import com.back.domain.trend.ranking.dto.RankedKeywordDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MmrDiversifier {

    public List<RankedKeywordDto> diversify(List<RankedKeywordDto> candidates,
                                             Map<String, Double> similarities,
                                             MmrConfig config,
                                             int topN) {
        if (candidates.isEmpty() || topN <= 0) {
            return List.of();
        }
        List<RankedKeywordDto> selected = new ArrayList<>();
        List<RankedKeywordDto> remaining = new ArrayList<>(candidates);
        remaining.sort((a, b) -> Double.compare(b.zScore(), a.zScore()));
        RankedKeywordDto rMax = remaining.get(0);
        selected.add(rMax);
        remaining.remove(rMax);

        while (selected.size() < topN && !remaining.isEmpty()) {
            RankedKeywordDto best = null;
            double bestPenalizedScore = Double.NEGATIVE_INFINITY;
            for (RankedKeywordDto candidate : remaining) {
                double sim = maxSimilarityToSelected(candidate, selected, similarities);
                if (sim >= config.similarityThreshold()) {
                    continue;
                }
                double penalizedScore = candidate.zScore() - config.alpha() * sim;
                if (best == null || penalizedScore > bestPenalizedScore) {
                    best = candidate;
                    bestPenalizedScore = penalizedScore;
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
            remaining.remove(best);
        }
        return selected;
    }

    private double maxSimilarityToSelected(RankedKeywordDto candidate, List<RankedKeywordDto> selected, Map<String, Double> similarities) {
        double maxSim = 0.0;
        for (RankedKeywordDto other : selected) {
            double sim = similarities.getOrDefault(KeywordPairKey.of(candidate.keyword(), other.keyword()), 0.0);
            if (sim > maxSim) {
                maxSim = sim;
            }
        }
        return maxSim;
    }
}
