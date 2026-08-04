package com.back.domain.chat.chatRoomMessage.event;

import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class ChatBroadcastListenerTest {
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatBroadcastListener listener;

    @Test
    @DisplayName("이벤트 수신 시 /topic/rooms/{roomId}로 RedisChatMessageDto를 broadcast한다")
    void t1() {
        UUID roomId = UUID.randomUUID();
        RedisChatMessageDto dto = new RedisChatMessageDto(
                UUID.randomUUID(), roomId, "테스트닉네임", UUID.randomUUID(), "테스트 메시지", LocalDateTime.now()
        );

        listener.onMessageSaved(new ChatMessageSentEvent(dto));

        verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId, dto);
    }

    @Test
    @DisplayName("서로 다른 roomId의 이벤트는 각각 올바른 토픽으로 broadcast된다")
    void t2() {
        UUID roomId1 = UUID.randomUUID();
        UUID roomId2 = UUID.randomUUID();
        RedisChatMessageDto dto1 = new RedisChatMessageDto(
                UUID.randomUUID(), roomId1, "유저A", UUID.randomUUID(), "방1 메시지", LocalDateTime.now()
        );
        RedisChatMessageDto dto2 = new RedisChatMessageDto(
                UUID.randomUUID(), roomId2, "유저B", UUID.randomUUID(), "방2 메시지", LocalDateTime.now()
        );

        listener.onMessageSaved(new ChatMessageSentEvent(dto1));
        listener.onMessageSaved(new ChatMessageSentEvent(dto2));

        verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId1, dto1);
        verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId2, dto2);
        verifyNoMoreInteractions(messagingTemplate);
    }

}
