package com.back.global.webSocket;

import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private final MemberService memberService;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;


    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new AccessDeniedException("WebSocket 연결에 토큰이 필요합니다.");
            }

            String token = authHeader.substring(7);
            Map<String, Object> payload = memberService.payload(token);
            if (payload == null) {
                throw new AccessDeniedException("유효하지 않은 토큰입니다.");
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
        }


        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();

            if (destination != null && destination.startsWith("/topic/rooms/")) {
                String roomIdStr = destination.replace("/topic/rooms/", "");
                try {
                    UUID roomId = UUID.fromString(roomIdStr);
                    UsernamePasswordAuthenticationToken auth =
                            (UsernamePasswordAuthenticationToken) accessor.getUser();
                    if (auth == null) {
                        throw new AccessDeniedException("인증이 필요합니다.");
                    }

                    UUID memberId = (UUID) auth.getDetails();
                    if (!chatRoomParticipantRepository.existsByChatRoomIdAndMemberId(roomId, memberId)) {
                        throw new AccessDeniedException("해당 채팅방의 참여자가 아닙니다.");
                    }
                } catch (IllegalArgumentException e) {
                    throw new AccessDeniedException("유효하지 않은 구독 경로입니다.");
                }
            }
        }
        return message;
    }
}