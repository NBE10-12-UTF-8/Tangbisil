package com.back.domain.chat.chatRoom.event

import com.back.domain.chat.chatRoom.dto.RoomClosedNotificationDto
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ChatRoomEventHandler(
    private val messagingTemplate: SimpMessagingTemplate
) {
    companion object {
        private val log = LoggerFactory.getLogger(ChatRoomEventHandler::class.java)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleChatRoomClosed(event: ChatRoomClosedEvent) {
        for (memberId in event.targetMemberIds) {
            try {
                messagingTemplate.convertAndSendToUser(
                    memberId,
                    "/queue/rooms/${event.roomId}",
                    RoomClosedNotificationDto.of()
                )
            } catch (e: Exception) {
                log.error("채팅방 종료 알림 브로드캐스트 실패 - roomId: {}, memberId: {}", event.roomId, memberId, e)
            }
        }
    }
}
