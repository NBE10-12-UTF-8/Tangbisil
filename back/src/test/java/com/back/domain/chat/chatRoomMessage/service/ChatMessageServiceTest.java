package com.back.domain.chat.chatRoomMessage.service;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomMessage.event.ChatMessageSentEvent;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import static com.back.domain.member.member.entity.Industry.IT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
public class ChatMessageServiceTest {
    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("메시지 전송 성공 시 ChatMessageSentEvent가 발행된다")
    void t1() {
        // Given
        Member member = memberService.joinWithoutEmailVerification("svc_user1@test.com", "1234", IT, "USER");
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, member, "익명의 동료"));

        // When
        chatMessageService.sendMessage(chatRoom.getId(), member, "이벤트 발행 테스트");

        // Then
        assertThat(events.stream(ChatMessageSentEvent.class).count()).isEqualTo(1);

        ChatMessageSentEvent event = events.stream(ChatMessageSentEvent.class).findFirst().orElseThrow();
        assertThat(event.getMessageDto().getContent()).isEqualTo("이벤트 발행 테스트");
        assertThat(event.getMessageDto().getRoomId()).isEqualTo(chatRoom.getId());
    }

    @Test
    @DisplayName("참여자가 아니면 예외가 발생하고 이벤트는 발행되지 않는다")
    void t2() {
        // Given
        Member outsider = memberService.joinWithoutEmailVerification("svc_user2@test.com", "1234", IT, "USER");
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        // 참여자로 등록하지 않음 — outsider는 이 방과 무관

        // When & Then
        assertThatThrownBy(() ->
                chatMessageService.sendMessage(chatRoom.getId(), outsider, "권한 없는 전송 시도")
        ).isInstanceOf(ServiceException.class);

        assertThat(events.stream(ChatMessageSentEvent.class).count()).isEqualTo(0);
    }

    @Test
    @DisplayName("종료된 채팅방이면 예외가 발생하고 이벤트는 발행되지 않는다")
    void t3() {
        // Given
        Member member = memberService.joinWithoutEmailVerification("svc_user3@test.com", "1234", IT, "USER");
        ChatRoom chatRoom = new ChatRoom(ChatRoomStatus.ACTIVE, 2);
        chatRoom.close();
        chatRoomRepository.save(chatRoom);

        // When & Then
        assertThatThrownBy(() ->
                chatMessageService.sendMessage(chatRoom.getId(), member, "종료된 방 전송 시도")
        ).isInstanceOf(ServiceException.class);

        assertThat(events.stream(ChatMessageSentEvent.class).count()).isEqualTo(0);
    }
}
