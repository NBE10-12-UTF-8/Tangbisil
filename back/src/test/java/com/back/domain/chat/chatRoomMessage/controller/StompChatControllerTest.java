package com.back.domain.chat.chatRoomMessage.controller;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomMessage.dto.ChatRoomMessageRequestDto;
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StompChatControllerTest {
    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private MemberService memberService;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @InjectMocks
    private StompChatController controller;

    private UsernamePasswordAuthenticationToken principalOf(UUID memberId) {
        return new UsernamePasswordAuthenticationToken(
                memberId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    @DisplayName("Principal이 null이면 ServiceException(401-1)을 던진다")
    void t1() {
        UUID roomId = UUID.randomUUID();
        ChatRoomMessageRequestDto requestDto = mock(ChatRoomMessageRequestDto.class);

        assertThatThrownBy(() -> controller.sendMessage(roomId, requestDto, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("인증이 필요합니다.");
    }

    @Test
    @DisplayName("유효한 Principal로 sendMessage 호출 시 ChatMessageService.sendMessage()가 위임된다")
    void t2() {
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String content = "테스트 메시지";

        Member mockMember = mock(Member.class);
        ChatRoom mockChatRoom = mock(ChatRoom.class);
        Long chatRoomLongId = 1L;
        when(mockChatRoom.getId()).thenReturn(chatRoomLongId);
        when(memberService.findByUuid(memberId)).thenReturn(Optional.of(mockMember));
        when(chatRoomRepository.findByUuid(roomId)).thenReturn(Optional.of(mockChatRoom));

        ChatRoomMessageRequestDto requestDto = mock(ChatRoomMessageRequestDto.class);
        when(requestDto.getContent()).thenReturn(content);

        controller.sendMessage(roomId, requestDto, principalOf(memberId));

        verify(chatMessageService).sendMessage(chatRoomLongId, mockMember, content);
    }

    @Test
    @DisplayName("memberId에 해당하는 회원이 없으면 ServiceException(404-1)을 던진다")
    void t3() {
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(memberService.findByUuid(memberId)).thenReturn(Optional.empty());

        ChatRoomMessageRequestDto requestDto = mock(ChatRoomMessageRequestDto.class);

        assertThatThrownBy(() -> controller.sendMessage(roomId, requestDto, principalOf(memberId)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("존재하지 않는 회원입니다.");
    }
}
