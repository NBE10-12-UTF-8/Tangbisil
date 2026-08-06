package com.back.domain.chat.chatRoom.scheduler

import com.back.domain.chat.chatRoom.entity.ChatRoomStatus
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class ChatRoomAutoCloseScheduler(
    private val chatRoomRepository: ChatRoomRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(ChatRoomAutoCloseScheduler::class.java)
    }

    // 1분 간격으로 검사
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    fun closeExpiredChatRooms() {
        // 기준 시간: 지금으로부터 10분 전
        val threshold = LocalDateTime.now().minusMinutes(10)

        val expiredRooms = chatRoomRepository.findByStatusAndCreatedAtBefore(ChatRoomStatus.ACTIVE, threshold)

        for (room in expiredRooms) {
            room.close()
        }

        if (expiredRooms.isNotEmpty()) {
            log.info("[채팅방 자동 종료] 10분 경과 활성 채팅방 {}건 자동 종료 완료", expiredRooms.size)
        }
    }
}
