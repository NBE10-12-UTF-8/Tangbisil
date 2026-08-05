package com.back.domain.match.matchRequest.dto

import com.back.domain.match.matchRequest.entity.MatchRequest
import com.back.domain.match.matchRequest.entity.MatchStatus
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MatchResponseDto(
    val matchRequestId: String?,
    val status: MatchStatus,
    val requestedAt: LocalDateTime?,
    val chatRoomId: String?
) {
    companion object {
        @JvmStatic
        fun ofCreated(matchRequest: MatchRequest): MatchResponseDto =
            MatchResponseDto(matchRequest.uuid.toString(), matchRequest.status, matchRequest.requestedAt, null)

        @JvmStatic
        fun ofMatched(chatRoomId: UUID): MatchResponseDto =
            MatchResponseDto(null, MatchStatus.MATCHED, null, chatRoomId.toString())

        @JvmStatic
        fun ofPending(): MatchResponseDto =
            MatchResponseDto(null, MatchStatus.PENDING, null, null)
    }
}
