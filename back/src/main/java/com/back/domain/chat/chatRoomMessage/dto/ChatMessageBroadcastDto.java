package com.back.domain.chat.chatRoomMessage.dto;

import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class ChatMessageBroadcastDto {
    private final UUID messageId;
    private final UUID roomId;
    private final UUID senderMemberId;
    private final String senderNickname;
    private final String content;
    private final LocalDateTime createdAt;

    public ChatMessageBroadcastDto(ChatMessage message) {
        this.messageId = message.getId();
        this.roomId = message.getChatRoom().getId();
        this.senderMemberId = message.getParticipant().getMember().getId();
        this.senderNickname = message.getParticipant().getNickname();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
    }
}

