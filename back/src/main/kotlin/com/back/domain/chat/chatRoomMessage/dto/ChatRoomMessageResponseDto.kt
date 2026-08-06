package com.back.domain.chat.chatRoomMessage.dto

import com.back.domain.chat.chatRoomMessage.entity.ChatMessage
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.UUID

data class ChatRoomMessageResponseDto(
    val messageId: UUID,
    val roomId: UUID,
    val senderNickname: String,
    val content: String?,
    val createdAt: LocalDateTime,
    @get:JsonProperty("isMine") val isMine: Boolean
) {
    constructor(message: ChatMessage, requesterUuid: UUID) : this(
        message.uuid,
        message.chatRoom.uuid,
        message.participant.nickname,
        message.content,
        message.createdAt!!,
        message.participant.member.uuid == requesterUuid
    )

    constructor(cache: RedisChatMessageDto, requesterParticipantId: UUID?) : this(
        cache.messageId,
        cache.roomId,
        cache.senderNickname,
        cache.content,
        cache.createdAt,
        requesterParticipantId != null && requesterParticipantId == cache.senderParticipantId
    )
}
