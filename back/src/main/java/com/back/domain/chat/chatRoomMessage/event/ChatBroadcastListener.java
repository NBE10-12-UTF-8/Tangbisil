package com.back.domain.chat.chatRoomMessage.event;

import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatBroadcastListener {
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSaved(ChatMessageSentEvent event) {
        RedisChatMessageDto dto = event.getMessageDto();
        messagingTemplate.convertAndSend("/topic/rooms/" + dto.getRoomId(), dto);
    }
}