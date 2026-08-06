package com.back.domain.dashboard.dashboard.dto

import java.time.LocalDate

data class IndustrySignupStatisticsResponseDto(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val industryStatistics: List<IndustryStatisticsDto>
)
