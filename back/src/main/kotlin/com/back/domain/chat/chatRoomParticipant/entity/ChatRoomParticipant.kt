package com.back.domain.chat.chatRoomParticipant.entity

import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.member.member.entity.Member
import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
class ChatRoomParticipant(
    chatRoom: ChatRoom,
    member: Member,
    nickname: String
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    var chatRoom: ChatRoom = chatRoom
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member = member
        protected set

    var nickname: String = nickname
        protected set

    var joinedAt: LocalDateTime = LocalDateTime.now()
        protected set
}
