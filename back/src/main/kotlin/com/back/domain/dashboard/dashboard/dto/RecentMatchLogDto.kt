package com.back.domain.dashboard.dashboard.dto

import com.back.domain.match.matchRequest.entity.Situation
import com.back.domain.member.member.entity.Industry
import java.time.LocalDateTime

// 매칭 이력에는 날짜, 산업군, 상황 정보만 저장한다는 보안 설계 원칙에 따라
// 회원 식별 정보(이메일, id 등)는 포함하지 않는다
data class RecentMatchLogDto(
    val matchedAt: LocalDateTime,
    val industry: Industry?,
    val situation: Situation?
)
