package com.back.domain.chat.chatRoomMessage.event;

import com.back.domain.chat.chatRoomMessage.dto.ChatMessageBroadcastDto;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatBroadcastListener {
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSaved(ChatMessageSavedEvent event) {
        ChatMessageBroadcastDto dto = event.broadcastDto();
        messagingTemplate.convertAndSend("/topic/rooms/" + dto.getRoomId(), dto);
    }
}