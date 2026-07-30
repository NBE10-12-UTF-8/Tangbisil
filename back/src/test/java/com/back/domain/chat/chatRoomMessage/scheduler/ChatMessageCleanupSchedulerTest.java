package com.back.domain.chat.chatRoomMessage.scheduler;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import com.back.domain.chat.chatRoomMessage.repository.ChatMessageRepository;
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

import java.time.LocalDateTime;

import static com.back.domain.member.member.entity.Industry.*;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class ChatMessageCleanupSchedulerTest {
    @Autowired private ChatMessageCleanupScheduler scheduler;
    @Autowired private MemberService memberService;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;

    @Test
    @DisplayName("종료 후 24시간 지난 방의 메시지는 삭제된다")
    void t1() {
        Member member = memberService.joinWithoutEmailVerification("cleanup1@test.com", "1234", IT, "USER");
        ChatRoom oldRoom = new ChatRoom(ChatRoomStatus.ACTIVE, 2);
        oldRoom.closeAtForTest(LocalDateTime.now().minusHours(25));
        chatRoomRepository.save(oldRoom);
        ChatRoomParticipant participant = chatRoomParticipantRepository.save(
                new ChatRoomParticipant(oldRoom, member, "익명의 동료"));
        chatMessageRepository.save(new ChatMessage(oldRoom, participant, "오래된 메시지"));

        scheduler.cleanupExpiredMessages();

        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    @DisplayName("종료 후 24시간 안 지난 방의 메시지는 유지된다")
    void t2() {
        Member member = memberService.joinWithoutEmailVerification("cleanup2@test.com", "1234", IT, "USER");
        ChatRoom recentRoom = new ChatRoom(ChatRoomStatus.ACTIVE, 2);
        recentRoom.closeAtForTest(LocalDateTime.now().minusHours(1));
        chatRoomRepository.save(recentRoom);
        ChatRoomParticipant participant = chatRoomParticipantRepository.save(
                new ChatRoomParticipant(recentRoom, member, "익명의 동료"));
        chatMessageRepository.save(new ChatMessage(recentRoom, participant, "최근 메시지"));

        scheduler.cleanupExpiredMessages();

        assertThat(chatMessageRepository.count()).isEqualTo(1);
    }
}