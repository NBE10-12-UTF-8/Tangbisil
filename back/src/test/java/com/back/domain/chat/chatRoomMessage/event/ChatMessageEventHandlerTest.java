package com.back.domain.chat.chatRoomMessage.event;

import com.back.domain.chat.chatRoomMessage.dto.BroadcastChatMessageDto;
import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Member;
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
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private ChatMessageEventHandler handler;

    private ChatRoomParticipant mockParticipant(UUID participantId, String email) {
        Member member = mock(Member.class);
        when(member.getEmail()).thenReturn(email);

        ChatRoomParticipant participant = mock(ChatRoomParticipant.class);
        when(participant.getId()).thenReturn(participantId);
        when(participant.getMember()).thenReturn(member);
        return participant;
    }

    @Test
    @DisplayName("Redis 적재 후 각 참여자에게 isMine이 설정된 메시지를 개별 전송한다")
    void t1() {
        UUID roomId = UUID.randomUUID();
        UUID senderParticipantId = UUID.randomUUID();
        UUID otherParticipantId = UUID.randomUUID();

        RedisChatMessageDto dto = new RedisChatMessageDto(
                UUID.randomUUID(), roomId, "테스트닉네임", senderParticipantId, "테스트 메시지", LocalDateTime.now()
        );

        ChatRoomParticipant sender = mockParticipant(senderParticipantId, "sender@test.com");
        ChatRoomParticipant other = mockParticipant(otherParticipantId, "other@test.com");

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(chatRoomParticipantRepository.findByChatRoomId(roomId))
                .thenReturn(List.of(sender, other));

        handler.handleChatMessageSent(new ChatMessageSentEvent(dto));

        ArgumentCaptor<BroadcastChatMessageDto> captor = ArgumentCaptor.forClass(BroadcastChatMessageDto.class);

        verify(messagingTemplate).convertAndSendToUser(eq("sender@test.com"),
                eq("/queue/rooms/" + roomId), captor.capture());
        assertThat(captor.getValue().getIsMine()).isTrue();

        verify(messagingTemplate).convertAndSendToUser(eq("other@test.com"),
                eq("/queue/rooms/" + roomId), captor.capture());
        assertThat(captor.getValue().getIsMine()).isFalse();


        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("서로 다른 roomId의 이벤트는 각각 올바른 경로로 개별 전송된다")
    void t2() {
        UUID roomId1 = UUID.randomUUID();
        UUID roomId2 = UUID.randomUUID();
        UUID participantId1 = UUID.randomUUID();
        UUID participantId2 = UUID.randomUUID();

        RedisChatMessageDto dto1 = new RedisChatMessageDto(
                UUID.randomUUID(), roomId1, "유저A", participantId1, "방1 메시지", LocalDateTime.now()
        );
        RedisChatMessageDto dto2 = new RedisChatMessageDto(
                UUID.randomUUID(), roomId2, "유저B", participantId2, "방2 메시지", LocalDateTime.now()
        );

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(chatRoomParticipantRepository.findByChatRoomId(roomId1))
                .thenReturn(List.of(mockParticipant(participantId1, "a@test.com")));
        when(chatRoomParticipantRepository.findByChatRoomId(roomId2))
                .thenReturn(List.of(mockParticipant(participantId2, "b@test.com")));

        handler.handleChatMessageSent(new ChatMessageSentEvent(dto1));
        handler.handleChatMessageSent(new ChatMessageSentEvent(dto2));

        verify(messagingTemplate).convertAndSendToUser(eq("a@test.com"),
                eq("/queue/rooms/" + roomId1), any(BroadcastChatMessageDto.class));
        verify(messagingTemplate).convertAndSendToUser(eq("b@test.com"),
                eq("/queue/rooms/" + roomId2), any(BroadcastChatMessageDto.class));
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Redis 적재 실패해도 참여자에게 broadcast는 실행된다")
    void t3() {
        UUID roomId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        RedisChatMessageDto dto = new RedisChatMessageDto(
                UUID.randomUUID(), roomId, "테스트닉네임", participantId, "테스트 메시지", LocalDateTime.now()
        );

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        doThrow(new RuntimeException("Redis 다운")).when(zSetOperations)
                .add(anyString(), anyString(), anyDouble());
        when(chatRoomParticipantRepository.findByChatRoomId(roomId))
                .thenReturn(List.of(mockParticipant(participantId, "user@test.com")));

        handler.handleChatMessageSent(new ChatMessageSentEvent(dto));

        verify(messagingTemplate).convertAndSendToUser(eq("user@test.com"),
                eq("/queue/rooms/" + roomId), any(BroadcastChatMessageDto.class));
    }

    @Test
    @DisplayName("봇 참여자에게는 broadcast하지 않는다")
    void t4() {
        UUID roomId = UUID.randomUUID();
        UUID humanParticipantId = UUID.randomUUID();
        UUID botParticipantId = UUID.randomUUID();

        RedisChatMessageDto dto = new RedisChatMessageDto(
                UUID.randomUUID(), roomId, "사람닉네임", humanParticipantId, "메시지", LocalDateTime.now()
        );

        ChatRoomParticipant human = mockParticipant(humanParticipantId, "human@test.com");
        ChatRoomParticipant bot = mockParticipant(botParticipantId, "bot.it@tangbisil.bot");

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(chatRoomParticipantRepository.findByChatRoomId(roomId))
                .thenReturn(List.of(human, bot));

        handler.handleChatMessageSent(new ChatMessageSentEvent(dto));

        verify(messagingTemplate).convertAndSendToUser(eq("human@test.com"),
                eq("/queue/rooms/" + roomId), any(BroadcastChatMessageDto.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("bot.it@tangbisil.bot"),
                anyString(), any());
    }
}
