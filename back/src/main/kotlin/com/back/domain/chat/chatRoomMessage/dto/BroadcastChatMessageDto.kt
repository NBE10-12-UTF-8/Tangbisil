package com.back.domain.chat.chatRoomMessage.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.UUID

data class BroadcastChatMessageDto(
    val messageId: UUID,
    val roomId: UUID,
    val senderNickname: String,
    val senderParticipantId: UUID,
    val content: String?,
    val createdAt: LocalDateTime,
    @get:JsonProperty("isMine") val isMine: Boolean
) {
    constructor(source: RedisChatMessageDto, isMine: Boolean) : this(
        source.messageId,
        source.roomId,
        source.senderNickname,
        source.senderParticipantId,
        source.content,
        source.createdAt,
        isMine
    )
}
