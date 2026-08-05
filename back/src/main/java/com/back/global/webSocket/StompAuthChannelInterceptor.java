package com.back.global.webSocket;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Member;
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
    private static final String ROOM_QUEUE_PREFIX = "/user/queue/rooms/";

    private final MemberService memberService;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;


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
            String role = (String) payload.get("role");

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    id.toString(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            accessor.setUser(auth);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination == null) throw new AccessDeniedException("구독 경로가 필요합니다.");

            if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)) {
                throw new AccessDeniedException("인증 정보가 올바르지 않습니다.");
            }
            UUID memberId = UUID.fromString(auth.getName());

            if (destination.equals("/user/queue/errors")) return message;
            if (!destination.startsWith(ROOM_QUEUE_PREFIX)) throw new AccessDeniedException("허용되지 않은 구독 경로입니다.");

            String roomIdStr = destination.substring(ROOM_QUEUE_PREFIX.length());
            UUID roomId;
            try {
                roomId = UUID.fromString(roomIdStr);
            } catch (IllegalArgumentException e) {
                throw new AccessDeniedException("유효하지 않은 구독 경로입니다.");
            }

            ChatRoom room = chatRoomRepository.findByUuid(roomId)
                    .orElseThrow(() -> new AccessDeniedException("존재하지 않는 채팅방입니다."));
            Member member = memberService.findByUuid(memberId)
                    .orElseThrow(() -> new AccessDeniedException("존재하지 않는 회원입니다."));

            if (!chatRoomParticipantRepository.existsByChatRoomIdAndMemberId(room.getId(), member.getId())) {
                throw new AccessDeniedException("해당 채팅방의 참여자가 아닙니다.");
            }
        }
        return message;
    }
}