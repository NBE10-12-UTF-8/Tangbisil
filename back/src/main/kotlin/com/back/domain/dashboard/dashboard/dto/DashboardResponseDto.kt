package com.back.domain.dashboard.dashboard.dto

data class DashboardResponseDto(
    val matchStatistics: MatchStatisticsDto,
    val industryStatistics: List<IndustryStatisticsDto>,
    val recentMatchLogs: List<RecentMatchLogDto>
)
