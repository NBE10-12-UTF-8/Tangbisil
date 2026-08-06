package com.back.domain.chat.chatRoom.repository

import com.back.domain.chat.chatRoom.entity.ChatRoom
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface ChatRoomRepository : JpaRepository<ChatRoom, Long> {
    fun countByStatus(status: ChatRoomStatus): Long

    fun findByStatusAndCreatedAtBefore(status: ChatRoomStatus, threshold: LocalDateTime): List<ChatRoom>

    fun findByUuid(uuid: UUID): ChatRoom?
}
