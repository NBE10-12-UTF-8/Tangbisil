package com.back.domain.chat.chatRoom.event;

import com.back.domain.chat.chatRoom.dto.RoomClosedNotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatRoomEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomEventHandler.class);

    private final SimpMessagingTemplate messagingTemplate;

    public ChatRoomEventHandler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChatRoomClosed(ChatRoomClosedEvent event) {
        for (String memberId : event.getTargetMemberIds()) {
            try {
                messagingTemplate.convertAndSendToUser(
                        memberId,
                        "/queue/rooms/" + event.getRoomId(),
                        RoomClosedNotificationDto.of()
                );
            } catch (Exception e) {
                log.error("채팅방 종료 알림 브로드캐스트 실패 - roomId: {}, memberId: {}",
                        event.getRoomId(), memberId, e);
            }
        }
    }
}
