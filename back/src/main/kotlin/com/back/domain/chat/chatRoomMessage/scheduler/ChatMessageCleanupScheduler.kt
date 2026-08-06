package com.back.domain.chat.chatRoomMessage.scheduler

import com.back.domain.chat.chatRoomMessage.repository.ChatMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class ChatMessageCleanupScheduler(
    private val chatMessageRepository: ChatMessageRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(ChatMessageCleanupScheduler::class.java)
    }

    @Scheduled(cron = "0 0 * * * *") // 매시 정각
    @Transactional
    fun cleanupExpiredMessages() {
        val threshold = LocalDateTime.now().minusHours(24)
        val deletedCount = chatMessageRepository.deleteMessagesInRoomsClosedBefore(threshold)
        log.info("[메시지 휘발] 종료 후 24시간 경과 메시지 {}건 삭제 완료", deletedCount)
    }
}
