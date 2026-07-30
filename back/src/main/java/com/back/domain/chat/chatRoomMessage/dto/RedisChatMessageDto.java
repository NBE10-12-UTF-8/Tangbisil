package com.back.domain.chat.chatRoomMessage.dto;

import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class RedisChatMessageDto implements Serializable {

    private UUID messageId;
    private UUID roomId;
    private String senderNickname;
    private UUID senderMemberId;
    private String content;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;

    public RedisChatMessageDto() {
    }

    public RedisChatMessageDto(UUID messageId, UUID roomId, String senderNickname, UUID senderMemberId, String content, LocalDateTime createdAt) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.senderNickname = senderNickname;
        this.senderMemberId = senderMemberId;
        this.content = content;
        this.createdAt = createdAt;
    }

    // Entity -> DTO 변환 생성자
    public RedisChatMessageDto(ChatMessage message) {
        this.messageId = message.getId();
        this.roomId = message.getChatRoom().getId();
        this.senderNickname = message.getParticipant().getNickname();
        this.senderMemberId = message.getParticipant().getMember().getId();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
    }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getRoomId() { return roomId; }
    public void setRoomId(UUID roomId) { this.roomId = roomId; }

    public String getSenderNickname() { return senderNickname; }
    public void setSenderNickname(String senderNickname) { this.senderNickname = senderNickname; }

    public UUID getSenderMemberId() { return senderMemberId; }
    public void setSenderMemberId(UUID senderMemberId) { this.senderMemberId = senderMemberId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}