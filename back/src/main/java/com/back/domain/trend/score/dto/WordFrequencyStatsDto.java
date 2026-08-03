package com.back.domain.trend.score.dto;

public record WordFrequencyStatsDto(
        long frequency,
        long totalMessages
) {}
