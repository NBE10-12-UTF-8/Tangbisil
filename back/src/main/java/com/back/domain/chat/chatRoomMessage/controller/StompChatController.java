package com.back.domain.chat.chatRoomMessage.controller;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageRequestDto;
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.exception.ServiceException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class StompChatController {
    private final ChatMessageService chatMessageService;
    private final MemberService memberService;
    private final ChatRoomRepository chatRoomRepository;

    @MessageMapping("/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable UUID roomId,
            @Payload @Valid ChatRoomMessageRequestDto requestDto,
            Principal principal
    ) {
        if (principal == null) {
            throw new ServiceException("401-1", "인증이 필요합니다.");
        }

        UUID memberId = UUID.fromString(principal.getName());
        Member actor = memberService.findByUuid(memberId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));
        ChatRoom chatRoom = chatRoomRepository.findByUuid(roomId)
                .orElseThrow(() -> new ServiceException("404-2", "채팅방을 찾을 수 없습니다."));
        chatMessageService.sendMessage(chatRoom.getId(), actor, requestDto.getContent());
    }

    @MessageExceptionHandler(ServiceException.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleException(ServiceException e) {
        return Map.of("code", e.getRsData().resultCode(), "message", e.getRsData().msg());
    }
}
