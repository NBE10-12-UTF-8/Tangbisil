package com.back.domain.chat.chatRoomMessage.event;

import com.back.domain.chat.chatRoomMessage.dto.BroadcastChatMessageDto;
import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatMessageEventHandlerTest {
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private ChatMessageEventHandler handler;

    private ChatMessageSentEvent.BroadcastTarget target(UUID participantId, String email) {
        return new ChatMessageSentEvent.BroadcastTarget(participantId, email, false);
    }

    private ChatMessageSentEvent.BroadcastTarget botTarget(UUID participantId, String email) {
        return new ChatMessageSentEvent.BroadcastTarget(participantId, email, true);
    }

    @Test
    @DisplayName("Redis 적재 후 각 참여자에게 isMine이 설정된 메시지를 개별 전송한다")
    void t1() {
        UUID roomId = UUID.randomUUID();
        UUID senderParticipantId = UUID.randomUUID();
        UUID otherParticipantId = UUID.randomUUID();
        String senderMemberId = UUID.randomUUID().toString();
        String otherMemberId = UUID.randomUUID().toString();

        RedisChatMessageDto dto = new RedisChatMessageDto(
                UUID.randomUUID(), roomId, "테스트닉네임", senderParticipantId, "테스트 메시지", LocalDateTime.now()
        );

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        handler.handleChatMessageSent(new ChatMessageSentEvent(dto, List.of(
            target(senderParticipantId, senderMemberId),
            target(otherParticipantId, otherMemberId)
        )));

        ArgumentCaptor<BroadcastChatMessageDto> captor = ArgumentCaptor.forClass(BroadcastChatMessageDto.class);

        verify(messagingTemplate).convertAndSendToUser(eq(senderMemberId),
                eq("/queue/rooms/" + roomId), captor.capture());
        assertThat(captor.getValue().isMine()).isTrue();

        verify(messagingTemplate).convertAndSendToUser(eq(otherMemberId),
                eq("/queue/rooms/" + roomId), captor.capture());
        assertThat(captor.getValue().isMine()).isFalse();

        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("서로 다른 roomId의 이벤트는 각각 올바른 경로로 개별 전송된다")
    void t2() {
        UUID roomId1 = UUID.randomUUID();
        UUID roomId2 = UUID.randomUUID();
        UUID participantId1 = UUID.randomUUID();
        UUID participantId2 = UUID.randomUUID();
        String memberId1 = UUID.randomUUID().toString();
        String memberId2 = UUID.randomUUID().toString();

        RedisChatMessageDto dto1 = new RedisChatMessageDto(
                UUID.randomUUID(), roomId1, "유저A", participantId1, "방1 메시지", LocalDateTime.now()
        );
        RedisChatMessageDto dto2 = new RedisChatMessageDto(
                UUID.randomUUID(), roomId2, "유저B", participantId2, "방2 메시지", LocalDateTime.now()
        );

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        handler.handleChatMessageSent(new ChatMessageSentEvent(dto1, List.of(target(participantId1, memberId1))));
        handler.handleChatMessageSent(new ChatMessageSentEvent(dto2, List.of(target(participantId2, memberId2))));

        verify(messagingTemplate).convertAndSendToUser(eq(memberId1),
                eq("/queue/rooms/" + roomId1), any(BroadcastChatMessageDto.class));
        verify(messagingTemplate).convertAndSendToUser(eq(memberId2),
                eq("/queue/rooms/" + roomId2), any(BroadcastChatMessageDto.class));
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Redis 적재 실패해도 참여자에게 broadcast는 실행된다")
    void t3() {
        UUID roomId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        String memberId = UUID.randomUUID().toString();

        RedisChatMessageDto dto = new RedisChatMessageDto(
                UUID.randomUUID(), roomId, "테스트닉네임", participantId, "테스트 메시지", LocalDateTime.now()
        );

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        doThrow(new RuntimeException("Redis 다운")).when(zSetOperations)
                .add(anyString(), anyString(), anyDouble());
        handler.handleChatMessageSent(new ChatMessageSentEvent(dto, List.of(target(participantId, memberId))));

        verify(messagingTemplate).convertAndSendToUser(eq(memberId),
                eq("/queue/rooms/" + roomId), any(BroadcastChatMessageDto.class));
    }

    @Test
    @DisplayName("봇 참여자에게는 broadcast하지 않는다")
    void t4() {
        UUID roomId = UUID.randomUUID();
        UUID humanParticipantId = UUID.randomUUID();
        UUID botParticipantId = UUID.randomUUID();
        String humanMemberId = UUID.randomUUID().toString();
        String botMemberId = UUID.randomUUID().toString();

        RedisChatMessageDto dto = new RedisChatMessageDto(
                UUID.randomUUID(), roomId, "사람닉네임", humanParticipantId, "메시지", LocalDateTime.now()
        );

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        handler.handleChatMessageSent(new ChatMessageSentEvent(dto, List.of(
            target(humanParticipantId, humanMemberId),
            botTarget(botParticipantId, botMemberId)
        )));

        verify(messagingTemplate).convertAndSendToUser(eq(humanMemberId),
                eq("/queue/rooms/" + roomId), any(BroadcastChatMessageDto.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq(botMemberId),
                anyString(), any());
    }
}
