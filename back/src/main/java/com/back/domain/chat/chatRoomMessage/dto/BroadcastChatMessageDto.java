package com.back.domain.chat.chatRoomMessage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

public record BroadcastChatMessageDto(
        UUID messageId,
        UUID roomId,
        String senderNickname,
        UUID senderParticipantId,
        String content,
        LocalDateTime createdAt,
        @JsonProperty("isMine") boolean isMine
) {
    public BroadcastChatMessageDto(RedisChatMessageDto source, boolean isMine) {
        this(
                source.getMessageId(),
                source.getRoomId(),
                source.getSenderNickname(),
                source.getSenderParticipantId(),
                source.getContent(),
                source.getCreatedAt(),
                isMine
        );
    }
}