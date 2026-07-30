package com.back.domain.chat.chatRoom.service;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.back.domain.member.member.entity.Industry.*;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class ChatRoomServiceTest {

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Test
    @DisplayName("채팅방 생성 성공")
    void t1() {
        // Given
        Member member1 = memberService.joinWithoutEmailVerification("chatroomuser1@test.com", "1234", IT

                , "USER");
        Member member2 = memberService.joinWithoutEmailVerification("chatroomuser2@test.com", "1234", IT, "USER");
        List<Member> members = List.of(member1, member2);

        // When
        ChatRoom chatRoom = chatRoomService.createChatRoom(members);

        // Then
        assertThat(chatRoom).isNotNull();
        assertThat(chatRoom.getId()).isNotNull();
        assertThat(chatRoom.getStatus()).isEqualTo(ChatRoomStatus.ACTIVE);
        assertThat(chatRoom.getMaxParticipants()).isEqualTo(2);

        // 참여자 확인
        List<ChatRoomParticipant> participants = chatRoomParticipantRepository.findByChatRoomId(chatRoom.getId());
        assertThat(participants).hasSize(2);

        // 각각의 유저가 올바르게 참여했는지 및 닉네임("익명의 동료") 검증
        assertThat(participants)
                .extracting(ChatRoomParticipant::getMember)
                .containsExactlyInAnyOrder(member1, member2);

        assertThat(participants)
                .extracting(ChatRoomParticipant::getNickname)
                .containsOnly("익명의 동료");
    }
}
