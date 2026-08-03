package com.back.domain.chat.chatRoomMessage.controller;

import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageRequestDto;
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class StompChatController {
    private final ChatMessageService chatMessageService;
    private final MemberService memberService;

    @MessageMapping("/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable UUID roomId,
            @Payload ChatRoomMessageRequestDto requestDto,
            Principal principal
    ) {
        if(principal == null) {
            throw new ServiceException("401-1", "인증이 필요합니다.");
        }

        UUID memberId = (UUID)((UsernamePasswordAuthenticationToken)principal).getDetails();
        Member actor = memberService.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));
        chatMessageService.sendMessage(roomId, actor, requestDto.getContent());
    }
}
