package com.back.domain.chat.chatRoom.event;

import java.util.List;
import java.util.UUID;

public class ChatRoomClosedEvent {

    private final UUID roomId;
    private final List<String> targetMemberIds;

    public ChatRoomClosedEvent(UUID roomId, List<String> targetMemberIds) {
        this.roomId = roomId;
        this.targetMemberIds = targetMemberIds;
    }

    public UUID getRoomId() { return roomId; }
    public List<String> getTargetMemberIds() { return targetMemberIds; }
}
