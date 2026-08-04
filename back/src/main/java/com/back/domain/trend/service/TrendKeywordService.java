package com.back.domain.trend.service;

import com.back.domain.trend.KeywordPairKey;
import com.back.domain.trend.mmr.MmrDiversifier;
import com.back.domain.trend.mmr.dto.MmrConfig;
import com.back.domain.trend.npmi.NpmiCalculator;
import com.back.domain.trend.npmi.dto.CooccurrenceStatsDto;
import com.back.domain.trend.ranking.TrendKeywordRanker;
import com.back.domain.trend.ranking.dto.RankedKeywordDto;
import com.back.domain.trend.snapshot.entity.DailyCooccurrenceCount;
import com.back.domain.trend.snapshot.entity.DailyMessageCount;
import com.back.domain.trend.snapshot.repository.DailyCooccurrenceCountRepository;
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TrendKeywordService {

    private final TrendKeywordRanker trendKeywordRanker;
    private final NpmiCalculator npmiCalculator;
    private final MmrDiversifier mmrDiversifier;
    private final DailyCooccurrenceCountRepository dailyCooccurrenceCountRepository;
    private final DailyMessageCountRepository dailyMessageCountRepository;

    public TrendKeywordService(TrendKeywordRanker trendKeywordRanker,
                                NpmiCalculator npmiCalculator,
                                MmrDiversifier mmrDiversifier,
                                DailyCooccurrenceCountRepository dailyCooccurrenceCountRepository,
                                DailyMessageCountRepository dailyMessageCountRepository) {
        this.trendKeywordRanker = trendKeywordRanker;
        this.npmiCalculator = npmiCalculator;
        this.mmrDiversifier = mmrDiversifier;
        this.dailyCooccurrenceCountRepository = dailyCooccurrenceCountRepository;
        this.dailyMessageCountRepository = dailyMessageCountRepository;
    }

    public List<RankedKeywordDto> getTrendingKeywords(LocalDate targetDate, int candidatePoolSize, int topN, MmrConfig config) {
        List<RankedKeywordDto> candidates = trendKeywordRanker.rank(targetDate, candidatePoolSize);
        if (candidates.isEmpty()) {
            return List.of();
        }
        long totalMessages = dailyMessageCountRepository.findByDate(targetDate)
                .map(DailyMessageCount::getTotalMessages)
                .orElse(0L);
        Map<String, Long> cooccurFrequencies = dailyCooccurrenceCountRepository.findAllByDate(targetDate)
                .stream()
                .collect(Collectors.toMap(
                        row -> KeywordPairKey.of(row.getKeywordA(), row.getKeywordB()),
                        DailyCooccurrenceCount::getFrequency
                ));
        Map<String, Double> similarities = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                RankedKeywordDto candidateI = candidates.get(i);
                RankedKeywordDto candidateJ = candidates.get(j);

                String key = KeywordPairKey.of(candidateI.keyword(), candidateJ.keyword());
                long freqXY = cooccurFrequencies.getOrDefault(key, 0L);

                CooccurrenceStatsDto stats = new CooccurrenceStatsDto(
                        candidateI.frequency(), candidateJ.frequency(), freqXY, totalMessages);
                double npmi = npmiCalculator.calculate(stats);

                similarities.put(key, npmi);
            }
        }
        return mmrDiversifier.diversify(candidates, similarities, config, topN);
    }
}
