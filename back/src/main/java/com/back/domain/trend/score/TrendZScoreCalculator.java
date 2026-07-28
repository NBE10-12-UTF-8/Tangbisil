package com.back.domain.trend.score;

import org.springframework.stereotype.Component;

@Component
public class TrendZScoreCalculator {

    public double calculate(long baselineFrequency, long baselineTotalMessages,
                             long currentFrequency, long currentTotalMessages) {
        throw new UnsupportedOperationException("TODO: 직접 구현하세요");
    }
}
