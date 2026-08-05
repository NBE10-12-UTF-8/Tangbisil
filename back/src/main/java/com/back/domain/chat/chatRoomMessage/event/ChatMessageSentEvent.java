package com.back.domain.chat.chatRoomMessage.event;

import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;

import java.util.List;
import java.util.UUID;

public class ChatMessageSentEvent {
    
    private final RedisChatMessageDto messageDto;
    private final List<BroadcastTarget> targets;

    public ChatMessageSentEvent(RedisChatMessageDto messageDto, List<BroadcastTarget> targets) {
        this.messageDto = messageDto;
        this.targets = targets;
    }

    public RedisChatMessageDto getMessageDto() {
        return messageDto;
    }
    public List<BroadcastTarget> getTargets() { return targets; }

    public record BroadcastTarget(UUID participantId, String memberId, boolean isBot) {}

}