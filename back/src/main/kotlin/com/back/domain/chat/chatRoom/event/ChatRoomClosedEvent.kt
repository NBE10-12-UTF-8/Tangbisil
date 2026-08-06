package com.back.domain.chat.chatRoom.event

import java.util.UUID

class ChatRoomClosedEvent(
    val roomId: UUID,
    val targetMemberIds: List<String>
)
