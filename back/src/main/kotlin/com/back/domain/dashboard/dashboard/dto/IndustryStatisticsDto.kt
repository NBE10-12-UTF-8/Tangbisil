package com.back.domain.dashboard.dashboard.dto

import com.back.domain.member.member.entity.Industry

data class IndustryStatisticsDto(
    val industry: Industry?,
    val count: Long
)
