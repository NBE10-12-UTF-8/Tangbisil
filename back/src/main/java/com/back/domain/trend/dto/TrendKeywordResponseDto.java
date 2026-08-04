package com.back.domain.trend.dto;

public record TrendKeywordResponseDto(
        int rank,
        String label,
        String trend
) {}
