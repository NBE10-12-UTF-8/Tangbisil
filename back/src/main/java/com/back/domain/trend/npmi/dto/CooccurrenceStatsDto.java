package com.back.domain.trend.npmi.dto;

public record CooccurrenceStatsDto(
        long freqX,
        long freqY,
        long freqXY,
        long totalMessages
) {}
