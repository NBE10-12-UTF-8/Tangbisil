package com.back.domain.chat.chatRoomMessage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDateTime;
import java.util.UUID;

public class BroadcastChatMessageDto {
    private UUID messageId;
    private UUID roomId;
    private String senderNickname;
    private UUID senderParticipantId;
    private String content;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;

    @JsonProperty("isMine")
    private boolean isMine;

    public BroadcastChatMessageDto(RedisChatMessageDto source, boolean isMine) {
        this.messageId = source.getMessageId();
        this.roomId = source.getRoomId();
        this.senderNickname = source.getSenderNickname();
        this.senderParticipantId = source.getSenderParticipantId();
        this.content = source.getContent();
        this.createdAt = source.getCreatedAt();
        this.isMine = isMine;
    }

    public UUID getMessageId() { return messageId; }
    public UUID getRoomId() { return roomId; }
    public String getSenderNickname() { return senderNickname; }
    public UUID getSenderParticipantId() { return senderParticipantId; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean getIsMine() { return isMine; }
}
