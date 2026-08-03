package com.back.domain.trend.ranking.dto;

public record RankedKeywordDto(
        String keyword,
        long frequency,
        double zScore
) {}
