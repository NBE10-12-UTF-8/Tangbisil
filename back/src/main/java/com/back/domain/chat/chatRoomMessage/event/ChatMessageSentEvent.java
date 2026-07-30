package com.back.domain.chat.chatRoomMessage.event;

import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;

public class ChatMessageSentEvent {
    
    private final RedisChatMessageDto messageDto;

    public ChatMessageSentEvent(RedisChatMessageDto messageDto) {
        this.messageDto = messageDto;
    }

    public RedisChatMessageDto getMessageDto() {
        return messageDto;
    }
}