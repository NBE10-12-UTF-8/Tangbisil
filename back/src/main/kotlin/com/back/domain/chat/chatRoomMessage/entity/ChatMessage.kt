package com.back.domain.chat.chatRoomMessage.entity

import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant
import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity
class ChatMessage(
    chatRoom: ChatRoom,
    participant: ChatRoomParticipant,
    content: String
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    var chatRoom: ChatRoom = chatRoom
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    var participant: ChatRoomParticipant = participant
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = content
        protected set
}
