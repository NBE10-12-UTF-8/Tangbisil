package com.back.domain.chat.chatRoomMessage.dto

import com.back.domain.chat.chatRoomMessage.entity.ChatMessage
import java.io.Serializable
import java.time.LocalDateTime
import java.util.UUID

data class RedisChatMessageDto(
    val messageId: UUID,
    val roomId: UUID,
    val senderNickname: String,
    val senderParticipantId: UUID,
    val content: String?,
    val createdAt: LocalDateTime
) : Serializable {
    // Entity -> DTO 변환 생성자
    constructor(message: ChatMessage) : this(
        message.uuid,
        message.chatRoom.uuid,
        message.participant.nickname,
        message.participant.uuid,
        message.content,
        message.createdAt
    )
}
