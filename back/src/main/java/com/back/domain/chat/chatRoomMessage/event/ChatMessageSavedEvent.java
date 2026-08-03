package com.back.domain.chat.chatRoomMessage.event;

import com.back.domain.chat.chatRoomMessage.dto.ChatMessageBroadcastDto;

public record ChatMessageSavedEvent(ChatMessageBroadcastDto broadcastDto) {
}
