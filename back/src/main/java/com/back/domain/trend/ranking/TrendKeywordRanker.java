package com.back.domain.trend.ranking;

import com.back.domain.trend.ranking.dto.RankedKeywordDto;
import com.back.domain.trend.score.TrendZScoreCalculator;
import com.back.domain.trend.score.dto.WordFrequencyStatsDto;
import com.back.domain.trend.snapshot.entity.DailyKeywordCount;
import com.back.domain.trend.snapshot.entity.DailyMessageCount;
import com.back.domain.trend.snapshot.repository.DailyKeywordCountRepository;
import com.back.domain.trend.snapshot.repository.DailyMessageCountRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class TrendKeywordRanker {

    private final DailyKeywordCountRepository dailyKeywordCountRepository;
    private final DailyMessageCountRepository dailyMessageCountRepository;
    private final TrendZScoreCalculator trendZScoreCalculator;

    public TrendKeywordRanker(DailyKeywordCountRepository dailyKeywordCountRepository,
                               DailyMessageCountRepository dailyMessageCountRepository,
                               TrendZScoreCalculator trendZScoreCalculator) {
        this.dailyKeywordCountRepository = dailyKeywordCountRepository;
        this.dailyMessageCountRepository = dailyMessageCountRepository;
        this.trendZScoreCalculator = trendZScoreCalculator;
    }

    public List<RankedKeywordDto> rank(LocalDate targetDate, int topN) {
        Optional<DailyMessageCount> messageCount = dailyMessageCountRepository.findByDate(targetDate);
        if (!messageCount.isPresent()) {
            return List.of();
        }
        LocalDate baselineDate = targetDate.minusDays(7);
        long baselineTotalMessages = dailyMessageCountRepository.findByDate(baselineDate)
                .map(DailyMessageCount::getTotalMessages)
                .orElse(0L);

        Map<String, Long> baselineFrequencies = dailyKeywordCountRepository.findAllByDate(baselineDate)
                .stream()
                .collect(Collectors.toMap(DailyKeywordCount::getKeyword, DailyKeywordCount::getFrequency));

        long currentTotalMessages = messageCount.get().getTotalMessages();

        List<RankedKeywordDto> ranked = dailyKeywordCountRepository.findAllByDate(targetDate).stream()
                .map(dkc -> {
                    long baselineFrequency = baselineFrequencies.getOrDefault(dkc.getKeyword(), 0L);
                    WordFrequencyStatsDto baseline = new WordFrequencyStatsDto(baselineFrequency, baselineTotalMessages);
                    WordFrequencyStatsDto current = new WordFrequencyStatsDto(dkc.getFrequency(), currentTotalMessages);
                    double zScore = trendZScoreCalculator.calculate(baseline, current);
                    return new RankedKeywordDto(dkc.getKeyword(), dkc.getFrequency(), zScore);
                })
                .collect(Collectors.toList());

        ranked.sort((a, b) -> Double.compare(b.zScore(), a.zScore()));
        return ranked.subList(0, Math.max(0, Math.min(topN, ranked.size())));
    }
}
