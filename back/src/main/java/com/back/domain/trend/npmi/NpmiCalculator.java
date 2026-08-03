package com.back.domain.trend.npmi;

import com.back.domain.trend.npmi.dto.CooccurrenceStatsDto;
import org.springframework.stereotype.Component;

@Component
public class NpmiCalculator {

    public double calculate(CooccurrenceStatsDto stats) {
        if (stats.totalMessages() <= 0) {
            return 0.0;
        }
        if (stats.freqXY() == 0) {
            return -1.0;
        }
        double pX = calculateProbability(stats.freqX(), stats.totalMessages());
        double pY = calculateProbability(stats.freqY(), stats.totalMessages());
        double pXY = calculateProbability(stats.freqXY(), stats.totalMessages());
        double pmi = calculatePmi(pXY, pX, pY);
        return -pmi / (Math.log(pXY) / Math.log(2));
    }

    private double calculateProbability(long freq, long totalMessages) {
        return (double) freq / totalMessages;
    }

    private double calculatePmi(double pXY, double pX, double pY) {
        return Math.log(pXY / (pX * pY)) / Math.log(2);
    }
}
