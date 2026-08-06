package com.back.domain.bot;

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.MatchStatus;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.match.matchRequest.service.MatchRequestService;
import com.back.domain.match.matchRequest.service.RedisMatchQueue;
import com.back.domain.match.scheduler.MatchScheduler;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

// BotMatchIntegrationTest.java - 전체 재작성
@ActiveProfiles("test")
@SpringBootTest
class BotMatchIntegrationTest {
    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private MatchScheduler matchScheduler;
    @Autowired
    private RedisMatchQueue redisMatchQueue;
    @Autowired
    private MatchRequestRepository matchRequestRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;

    private final List<Member> createdMembers = new ArrayList<>();
    // createPendingRequest()로 Redis ZSet에 직접 시딩한 항목들 - 매칭/봇 폴백으로 이미 ZREM됐을 수도 있어서
    // 테스트 종료 시점에 남아있는 것만 정리한다 (이미 없는 멤버 제거는 no-op).
    private record QueueEntry(Industry industry, Situation situation, UUID id) {}
    private final List<QueueEntry> queuedEntries = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        queuedEntries.forEach(e -> redisMatchQueue.remove(e.industry(), e.situation(), e.id()));
        queuedEntries.clear();
        matchRequestRepository.deleteAll();
        chatRoomParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        createdMembers.forEach(memberRepository::delete);
        createdMembers.clear();
    }

    // 매칭 후보 조회는 이제 Redis ZSet만 보므로, DB 저장과 함께 ZADD도 직접 해준다.
    private MatchRequest createPendingRequest(Member member, Situation situation, long secondsAgo) {
        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(member, situation));
        LocalDateTime requestedAt = LocalDateTime.now().minusSeconds(secondsAgo);
        ReflectionTestUtils.setField(matchRequest, "requestedAt", requestedAt);
        MatchRequest saved = matchRequestRepository.saveAndFlush(matchRequest);

        long epochMilli = requestedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        redisMatchQueue.add(member.getIndustry(), situation, saved.getUuid(), epochMilli);
        queuedEntries.add(new QueueEntry(member.getIndustry(), situation, saved.getUuid()));

        return saved;
    }

    @Test
    @DisplayName("35초가 안 지났으면 실제 유저가 없어도 봇과 매칭되지 않는다")
    void 그레이스_기간_전에는_봇_매칭_안됨() {
        Member user = memberService.joinWithoutEmailVerification("bot_grace_early@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(user);
        MatchRequest userRequest = createPendingRequest(user, Situation.NIGHT_WORK, 32);

        matchRequestService.tryMatch(userRequest);

        MatchRequest refreshed = matchRequestRepository.findById(userRequest.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(MatchStatus.PENDING);
    }

    @Test
    @DisplayName("35초가 지나면 실제 유저를 못 찾은 요청이 봇과 매칭된다")
    void 그레이스_기간_지나면_봇과_매칭() {
        Member user = memberService.joinWithoutEmailVerification("bot_grace_fallback@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(user);
        MatchRequest userRequest = createPendingRequest(user, Situation.NIGHT_WORK, 36);

        matchRequestService.tryMatch(userRequest);

        MatchRequest refreshed = matchRequestRepository.findById(userRequest.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(refreshed.getRoom()).isNotNull();
    }

    @Test
    @DisplayName("실제 유저가 있으면 40초가 지나도 봇이 아니라 실제 유저와 매칭된다")
    void 실유저_있으면_봇보다_우선() {
        Member userA = memberService.joinWithoutEmailVerification("bot_priority_a@test.com", "1234", Industry.IT, "USER");
        Member userB = memberService.joinWithoutEmailVerification("bot_priority_b@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(userA);
        createdMembers.add(userB);
        MatchRequest reqA = createPendingRequest(userA, Situation.NIGHT_WORK, 36);
        MatchRequest reqB = createPendingRequest(userB, Situation.MEETING_BOMB, 36);

        matchScheduler.retryPendingMatches();

        MatchRequest refreshedA = matchRequestRepository.findById(reqA.getId()).orElseThrow();
        MatchRequest refreshedB = matchRequestRepository.findById(reqB.getId()).orElseThrow();
        assertThat(refreshedA.getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(refreshedB.getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(refreshedA.getRoom().getId()).isEqualTo(refreshedB.getRoom().getId());

        Member bot = memberRepository.findByEmail(BotAccounts.emailFor(Industry.IT));
        boolean botHasMatchRequest = matchRequestRepository.findAll().stream()
                .anyMatch(r -> r.getMember().getId().equals(bot.getId()));
        assertThat(botHasMatchRequest).isFalse();
    }
}