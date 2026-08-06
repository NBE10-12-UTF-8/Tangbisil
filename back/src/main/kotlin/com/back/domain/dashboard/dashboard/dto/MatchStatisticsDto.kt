package com.back.domain.dashboard.dashboard.dto

data class MatchStatisticsDto(
    val totalMembers: Long,
    val todayMatches: Long,
    val activeChatRooms: Long,
    val pendingMatches: Long
)
