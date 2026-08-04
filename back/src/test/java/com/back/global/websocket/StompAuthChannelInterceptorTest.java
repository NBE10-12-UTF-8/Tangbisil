package com.back.global.websocket;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StompAuthChannelInterceptorTest {

    @Mock
    MemberService memberService;

    @InjectMocks
    StompAuthChannelInterceptor interceptor;

    private StompHeaderAccessor mutableAccessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    @Test
    @DisplayName("CONNECT 시 유효한 토큰이면 UUID·이메일·role이 Principal에 정확히 설정된다")
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
        assertThat(auth.getDetails()).isEqualTo(memberId);
        assertThat(auth.getPrincipal()).isEqualTo("user1@test.com");
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

        interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(accessor.getUser()).isNull();
    }

    @Test
    @DisplayName("만료·위조 토큰(payload null)이면 Principal을 설정하지 않는다")
    void t4() {
        when(memberService.payload("bad-token")).thenReturn(null);

        StompHeaderAccessor accessor = mutableAccessor(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer bad-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(accessor.getUser()).isNull();
    }
}