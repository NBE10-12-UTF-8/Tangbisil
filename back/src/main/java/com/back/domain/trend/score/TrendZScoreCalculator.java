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

    // 1단계: p̂₀ = baseline.frequency / baseline.totalMessages
    private double calculateBaselineRatio(WordFrequencyStatsDto baseline) {
        double baselineRatio = (double) baseline.frequency() / baseline.totalMessages();
        return baselineRatio;
    }

    // 2단계: p̂₁ = current.frequency / current.totalMessages
    private double calculateCurrentRatio(WordFrequencyStatsDto current) {
        double currentRatio = (double)current.frequency() / current.totalMessages();
        return currentRatio;
    }

    // 3단계: p̂ = (baseline.frequency + current.frequency) / (baseline.totalMessages + current.totalMessages)
    private double calculatePooledRatio(WordFrequencyStatsDto baseline, WordFrequencyStatsDto current) {
        double pooledRatio = (double)(baseline.frequency() + current.frequency()) / (baseline.totalMessages() + current.totalMessages());
        return pooledRatio;
    }

    // 4단계: √[ pooledRatio × (1 − pooledRatio) × (1/current.totalMessages + 1/baseline.totalMessages) ]
    private double calculateStandardError(double pooledRatio, WordFrequencyStatsDto baseline, WordFrequencyStatsDto current) {
        double standardError = Math.sqrt(pooledRatio * (1 - pooledRatio) * (1 / (double)current.totalMessages() + 1 / (double)baseline.totalMessages()));
        return standardError;
    }
}
