package com.back.domain.trend.ranking.dto

data class RankedKeywordDto(
    val keyword: String,
    val frequency: Long,
    val zScore: Double
)
