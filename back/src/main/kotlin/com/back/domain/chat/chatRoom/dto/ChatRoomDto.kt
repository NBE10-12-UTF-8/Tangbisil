package com.back.domain.chat.chatRoom.dto

import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import com.back.domain.match.matchRequest.entity.Situation
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChatRoomDto(
    val roomId: UUID,
    val status: ChatRoomStatus,
    val maxParticipants: Int,
    val createdAt: LocalDateTime,
    val closedAt: LocalDateTime?,
    @get:JsonProperty("isBot") val isBot: Boolean,
    val opponentSituation: Situation?
) {
    constructor(chatRoom: ChatRoom, isBot: Boolean, opponentSituation: Situation?) : this(
        chatRoom.uuid,
        chatRoom.status,
        chatRoom.maxParticipants,
        chatRoom.createdAt,
        chatRoom.closedAt,
        isBot,
        opponentSituation
    )
}
