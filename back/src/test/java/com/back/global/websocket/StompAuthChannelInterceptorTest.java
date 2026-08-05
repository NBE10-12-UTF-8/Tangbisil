package com.back.global.websocket;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.webSocket.StompAuthChannelInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StompAuthChannelInterceptorTest {

    @Mock
    MemberService memberService;

    @Mock
    ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Mock
    ChatRoomRepository chatRoomRepository;

    @InjectMocks
    StompAuthChannelInterceptor interceptor;

    private StompHeaderAccessor mutableAccessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    @Test
    @DisplayName("CONNECT 시 유효한 토큰이면 memberId·role이 Principal에 정확히 설정된다")
    void t1() {
        UUID memberId = UUID.randomUUID();
        when(memberService.payload("valid-token")).thenReturn(Map.of(
                "id", memberId.toString(),
                "email", "user1@test.com",
                "role", "USER"
        ));

        StompHeaderAccessor accessor = mutableAccessor(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, mock(MessageChannel.class));

        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) accessor.getUser();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(memberId.toString());
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    @DisplayName("CONNECT 시 세션 attributes에 쿠키로 검증된 신원이 있으면 Authorization 헤더 없이도 인증된다")
    void t1b() {
        UUID memberId = UUID.randomUUID();

        StompHeaderAccessor accessor = mutableAccessor(StompCommand.CONNECT);
        accessor.setSessionAttributes(new java.util.HashMap<>(Map.of("memberId", memberId, "role", "USER")));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, mock(MessageChannel.class));

        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) accessor.getUser();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(memberId.toString());
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    @DisplayName("CONNECT가 아닌 프레임은 그냥 통과시킨다")
    void t2() {
        StompHeaderAccessor accessor = mutableAccessor(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(result).isEqualTo(message);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    @DisplayName("토큰이 없으면 Principal을 설정하지 않는다")
    void t3() {
        StompHeaderAccessor accessor = mutableAccessor(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("만료·위조 토큰(payload null)이면 Principal을 설정하지 않는다")
    void t4() {
        when(memberService.payload("bad-token")).thenReturn(null);

        StompHeaderAccessor accessor = mutableAccessor(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer bad-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SUBSCRIBE 시 채팅방 참여자가 아니면 AccessDeniedException")
    void t5() {
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                memberId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        StompHeaderAccessor accessor = mutableAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/rooms/" + roomId);
        accessor.setUser(auth);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getId()).thenReturn(1L);
        Member mockMember = mock(Member.class);
        when(mockMember.getId()).thenReturn(2L);
        when(chatRoomRepository.findByUuid(roomId)).thenReturn(java.util.Optional.of(mockRoom));
        when(memberService.findByUuid(memberId)).thenReturn(java.util.Optional.of(mockMember));
        when(chatRoomParticipantRepository.existsByChatRoomIdAndMemberId(1L, 2L))
                .thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SUBSCRIBE 시 채팅방 참여자이면 정상 통과한다")
    void t6() {
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                memberId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        StompHeaderAccessor accessor = mutableAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/rooms/" + roomId);
        accessor.setUser(auth);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        ChatRoom mockRoom = mock(ChatRoom.class);
        when(mockRoom.getId()).thenReturn(1L);
        Member mockMember = mock(Member.class);
        when(mockMember.getId()).thenReturn(2L);
        when(chatRoomRepository.findByUuid(roomId)).thenReturn(java.util.Optional.of(mockRoom));
        when(memberService.findByUuid(memberId)).thenReturn(java.util.Optional.of(mockMember));
        when(chatRoomParticipantRepository.existsByChatRoomIdAndMemberId(1L, 2L))
                .thenReturn(true);

        assertThatCode(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("/user/queue/errors 구독은 참여자 검사 없이 통과한다")
    void t7() {
        UUID memberId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                memberId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        StompHeaderAccessor accessor = mutableAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/errors");
        accessor.setUser(auth);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatCode(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용되지 않은 구독 경로이면 AccessDeniedException")
    void t8() {
        StompHeaderAccessor accessor = mutableAccessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }
}