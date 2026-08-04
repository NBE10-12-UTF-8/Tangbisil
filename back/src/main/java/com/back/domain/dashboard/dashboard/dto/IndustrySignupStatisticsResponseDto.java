package com.back.domain.dashboard.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record IndustrySignupStatisticsResponseDto(
        LocalDate startDate,
        LocalDate endDate,
        List<IndustryStatisticsDto> industryStatistics
) {}