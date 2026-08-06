package com.back.domain.trend.npmi.dto

data class CooccurrenceStatsDto(
    val freqX: Long,
    val freqY: Long,
    val freqXY: Long,
    val totalMessages: Long
)
