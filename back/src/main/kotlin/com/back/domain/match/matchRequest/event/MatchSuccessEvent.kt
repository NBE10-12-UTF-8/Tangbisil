package com.back.domain.match.matchRequest.event

import java.util.UUID

data class MatchSuccessEvent(
    val roomId: UUID,
    val requesterId: Long,
    val opponentId: Long
)
