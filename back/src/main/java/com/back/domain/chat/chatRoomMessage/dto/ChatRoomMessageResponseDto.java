package com.back.domain.chat.chatRoomMessage.dto;

import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class ChatRoomMessageResponseDto {
    private final UUID messageId;
    private final UUID roomId;
    private final String senderNickname;
    private final String content;
    private final LocalDateTime createdAt;
    @Getter(AccessLevel.NONE)
    private final boolean isMine;

    public boolean getIsMine() { return isMine; }

    public ChatRoomMessageResponseDto(ChatMessage message, UUID requesterUuid) {
        this.messageId = message.getUuid();
        this.roomId = message.getChatRoom().getUuid();
        this.senderNickname = message.getParticipant().getNickname();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
        this.isMine = message.getParticipant().getMember().getUuid().equals(requesterUuid);
    }

    public ChatRoomMessageResponseDto(RedisChatMessageDto cache, UUID requesterParticipantId) {
        this.messageId = cache.getMessageId();
        this.roomId = cache.getRoomId();
        this.senderNickname = cache.getSenderNickname();
        this.content = cache.getContent();
        this.createdAt = cache.getCreatedAt();
        this.isMine = requesterParticipantId != null && requesterParticipantId.equals(cache.getSenderParticipantId());
    }
}
