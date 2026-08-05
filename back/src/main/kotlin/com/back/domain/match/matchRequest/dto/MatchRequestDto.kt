package com.back.domain.match.matchRequest.dto

import com.back.domain.match.matchRequest.entity.Situation
import jakarta.validation.constraints.NotNull

data class MatchRequestDto(
    @field:NotNull(message = "상황을 선택해주세요.")
    val situation: Situation?
)
