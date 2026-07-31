package com.back.domain.trend.score;

import com.back.domain.trend.score.dto.WordFrequencyStatsDto;
import org.springframework.stereotype.Component;

@Component
public class TrendZScoreCalculator {

    public double calculate(WordFrequencyStatsDto baseline, WordFrequencyStatsDto current) {
        if (baseline.totalMessages() <= 0 || current.totalMessages() <= 0) {
            return 0.0;
        }
        double baselineRatio = calculateBaselineRatio(baseline);
        double currentRatio = calculateCurrentRatio(current);
        double pooledRatio = calculatePooledRatio(baseline, current);
        double standardError = calculateStandardError(pooledRatio, baseline, current);
        double zScore = (currentRatio - baselineRatio) / standardError;
        if (Double.isNaN(zScore) || Double.isInfinite(zScore)) {
            return 0.0;
        }
        return zScore;
    }

    private double calculateBaselineRatio(WordFrequencyStatsDto baseline) {
        return (double) baseline.frequency() / baseline.totalMessages();
    }

    private double calculateCurrentRatio(WordFrequencyStatsDto current) {
        return (double) current.frequency() / current.totalMessages();
    }

    private double calculatePooledRatio(WordFrequencyStatsDto baseline, WordFrequencyStatsDto current) {
        return (double) (baseline.frequency() + current.frequency()) / (baseline.totalMessages() + current.totalMessages());
    }

    private double calculateStandardError(double pooledRatio, WordFrequencyStatsDto baseline, WordFrequencyStatsDto current) {
        return Math.sqrt(pooledRatio * (1 - pooledRatio) * (1 / (double) current.totalMessages() + 1 / (double) baseline.totalMessages()));
    }
}
