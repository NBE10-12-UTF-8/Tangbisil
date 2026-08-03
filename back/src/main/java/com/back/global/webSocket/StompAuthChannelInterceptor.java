package com.back.global.webSocket;

import com.back.domain.member.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private final MemberService memberService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if(authHeader == null || !authHeader.startsWith("Bearer ")) {
            return message;
        }

        String token = authHeader.substring(7);
        Map<String, Object> payload = memberService.payload(token);
        if(payload == null) {
            return message;
        }

        Object rawId = payload.get("id");
        UUID id = (rawId instanceof UUID u) ? u : UUID.fromString(rawId.toString());
        String email = (String) payload.get("email");
        String role = (String) payload.get("role");

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        auth.setDetails(id);
        accessor.setUser(auth);
        return message;

    }


}
