package com.back.domain.chat.chatRoom.entity

import com.back.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.LocalDateTime

@Entity
class ChatRoom(
    status: ChatRoomStatus,
    maxParticipants: Int
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    var status: ChatRoomStatus = status
        protected set

    @Column(name = "max_participants")
    var maxParticipants: Int = maxParticipants
        protected set

    var closedAt: LocalDateTime? = null
        protected set

    fun close() {
        this.status = ChatRoomStatus.CLOSED
        this.closedAt = LocalDateTime.now()
    }

    // 테스트 전용: 24시간 휘발 스케줄러 검증을 위해 과거 종료 시각을 주입
    fun closeAtForTest(closedAt: LocalDateTime) {
        this.status = ChatRoomStatus.CLOSED
        this.closedAt = closedAt
    }
}
