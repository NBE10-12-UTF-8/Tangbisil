package com.back.domain.bot;

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import com.back.domain.chat.chatRoomMessage.repository.ChatMessageRepository;
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.match.matchRequest.service.MatchRequestService;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.member.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 봇의 자동 응답은 @TransactionalEventListener(AFTER_COMMIT)로 동작한다.
// 일반 @Transactional 테스트는 끝나면 롤백만 하고 절대 커밋을 안 해서 이 이벤트가 발화되지 않는다.
// 그래서 이 테스트만 예외적으로 @Transactional을 빼고 실제 커밋 + 수동 정리 방식으로 짠다.
@ActiveProfiles("test")
@SpringBootTest
class BotAutoReplyEndToEndTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private MatchRequestRepository matchRequestRepository;
    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;

    private Member createdUser;

    @AfterEach
    void cleanUp() {
        chatMessageRepository.deleteAll();
        chatRoomParticipantRepository.deleteAll();
        matchRequestRepository.deleteAll();
        chatRoomRepository.deleteAll();
        if (createdUser != null) {
            memberRepository.delete(createdUser);
            createdUser = null;
        }
    }

    @Test
    @DisplayName("봇과 매칭되면, 커밋 후 봇이 자동으로 메시지를 보낸다 (테스트 환경엔 GROQ_API_KEY가 없어 캔드 메시지로 폴백됨)")
    void 봇_매칭_후_자동응답() throws InterruptedException {
        // Given - 41초 전에 혼자 요청해서 봇 폴백 대상이 되게 함
        createdUser = memberService.joinWithoutEmailVerification("bot_reply_e2e@test.com", "1234", Industry.IT, "USER");
        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(createdUser, Situation.NIGHT_WORK));
        ReflectionTestUtils.setField(matchRequest, "requestedAt", LocalDateTime.now().minusSeconds(41));
        matchRequestRepository.saveAndFlush(matchRequest);

        // When - 매칭 트리거. 이 메서드가 반환되는 시점엔 트랜잭션이 커밋되어 있어야
        // AFTER_COMMIT 이벤트가 발화되고 봇 응답이 비동기로 시작된다.
        // (matchRequest는 이 테스트가 @Transactional이 아니라서 이미 detach된 상태라,
        // ID 기반 오버로드로 조회+매칭을 같은 트랜잭션 안에서 처리한다)
        matchRequestService.tryMatch(matchRequest.getUuid());

        MatchRequest matched = matchRequestRepository.findById(matchRequest.getId()).orElseThrow();
        Long roomId = matched.getRoom().getId();

        // Then - 비동기 + 최소 딜레이가 있어서 넉넉히 폴링
        boolean botReplied = false;
        for (int i = 0; i < 20; i++) {
            TimeUnit.MILLISECONDS.sleep(500);
            List<ChatMessage> messages = chatMessageService.getRecentMessages(roomId, 10);
            if (!messages.isEmpty()) {
                botReplied = true;
                assertThat(BotReplyMessages.LINES).contains(messages.get(0).getContent());
                break;
            }
        }
        assertThat(botReplied).isTrue();
    }
}