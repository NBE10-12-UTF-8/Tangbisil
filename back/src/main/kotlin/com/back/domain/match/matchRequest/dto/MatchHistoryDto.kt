package com.back.domain.match.matchRequest.dto

import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import com.back.domain.match.matchRequest.entity.MatchRequest
import com.back.domain.match.matchRequest.entity.Situation
import com.back.domain.member.member.entity.Industry
import java.time.LocalDateTime

data class MatchHistoryDto(
    val matchedAt: LocalDateTime,
    val industry: Industry?,
    val situation: Situation,
    val status: ChatRoomStatus,
    val isBot: Boolean
) {
    // findMatchHistoryByMember가 room.status = CLOSED 조건으로 조회한 결과만 넘어오므로 room은 항상 존재한다
    constructor(matchRequest: MatchRequest, isBot: Boolean) : this(
        matchRequest.room!!.createdAt!!,
        matchRequest.industry,
        matchRequest.situation,
        matchRequest.room!!.status,
        isBot
    )
}
