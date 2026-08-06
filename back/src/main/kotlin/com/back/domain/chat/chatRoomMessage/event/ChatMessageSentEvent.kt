package com.back.domain.chat.chatRoomMessage.event

import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto
import java.util.UUID

class ChatMessageSentEvent(
    val messageDto: RedisChatMessageDto,
    val targets: List<BroadcastTarget>
) {
    data class BroadcastTarget(val participantId: UUID, val memberId: String, val isBot: Boolean)
}
