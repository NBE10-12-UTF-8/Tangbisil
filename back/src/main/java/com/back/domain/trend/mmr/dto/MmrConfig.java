package com.back.domain.trend.mmr.dto;

public record MmrConfig(
        double alpha,
        double similarityThreshold
) {}
