package com.back.domain.match.matchRequest.service;

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.MatchStatus;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.back.domain.match.matchRequest.entity.Situation.*;
import static com.back.domain.member.member.entity.Industry.IT;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
public class MatchRequestServiceTest {

    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private MatchRequestRepository matchRequestRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private MatchNotificationService matchNotificationService;

    private final List<Member> createdMembers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        matchRequestRepository.deleteAll();
        chatRoomParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        createdMembers.forEach(memberRepository::delete);
        createdMembers.clear();
    }
    private Member createMember(String email) {
        Member member = memberRepository.save(new Member(email, "1234", IT, "USER"));
        createdMembers.add(member);
        return member;
    }

    private MatchRequest createPendingRequest(Member member, Situation situation, long secondsAgo) {
        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(member, situation));
        ReflectionTestUtils.setField(matchRequest, "requestedAt", LocalDateTime.now().minusSeconds(secondsAgo));
        return matchRequestRepository.saveAndFlush(matchRequest);
    }

    @Test
    @DisplayName("Tier 0: 같은 상황이면 대기 시간에 관계없이 즉시 매칭된다")
    void t1() {
        Member memberA = createMember("userA@test.com");
        Member memberB = createMember("userB@test.com");
        MatchRequest reqA = createPendingRequest(memberA, NIGHT_WORK, 0);
        MatchRequest reqB = createPendingRequest(memberB, NIGHT_WORK, 0);

        matchRequestService.retryPendingMatches();

        assertThat(matchRequestRepository.findById(reqA.getId()).get().getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(matchRequestRepository.findById(reqB.getId()).get().getStatus()).isEqualTo(MatchStatus.MATCHED);

        assertThat(matchNotificationService.getNotifications(memberA.getId(), null)).hasSize(1);
    }

    @Test
    @DisplayName("Tier 1: 상황이 다르고 15초가 지나지 않으면 매칭되지 않는다")
    void t2() {
        Member memberA = createMember("userA@test.com");
        Member memberB = createMember("userB@test.com");
        MatchRequest reqA = createPendingRequest(memberA, NIGHT_WORK, 10);
        MatchRequest reqB = createPendingRequest(memberB, MEETING_BOMB, 10);

        matchRequestService.retryPendingMatches();

        assertThat(matchRequestRepository.findById(reqA.getId()).get().getStatus()).isEqualTo(MatchStatus.PENDING);
        assertThat(matchRequestRepository.findById(reqB.getId()).get().getStatus()).isEqualTo(MatchStatus.PENDING);
    }

    @Test
    @DisplayName("Tier 1: 15초 후 유사 상황 그룹끼리 매칭된다 (야근 중 ↔ 회의 폭탄)")
    void t3() {
        Member memberA = createMember("userA@test.com");
        Member memberB = createMember("userB@test.com");
        MatchRequest reqA = createPendingRequest(memberA, NIGHT_WORK, 16);
        MatchRequest reqB = createPendingRequest(memberB, MEETING_BOMB, 16);

        matchRequestService.retryPendingMatches();

        assertThat(matchRequestRepository.findById(reqA.getId()).get().getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(matchRequestRepository.findById(reqB.getId()).get().getStatus()).isEqualTo(MatchStatus.MATCHED);
    }

    @Test
    @DisplayName("Tier 1: 유사 그룹이 없는 상황은 15~30초 사이에도 매칭되지 않는다")
    void t4() {
        Member memberA = createMember("userA@test.com");
        Member memberB = createMember("userB@test.com");
        MatchRequest reqA = createPendingRequest(memberA, SLACKING, 20);
        MatchRequest reqB = createPendingRequest(memberB, NIGHT_WORK, 20);

        matchRequestService.retryPendingMatches();

        assertThat(matchRequestRepository.findById(reqA.getId()).get().getStatus()).isEqualTo(MatchStatus.PENDING);
        assertThat(matchRequestRepository.findById(reqB.getId()).get().getStatus()).isEqualTo(MatchStatus.PENDING);
    }

    @Test
    @DisplayName("Tier 2: 30초 후 상황이 달라도 같은 업종이면 매칭된다")
    void t5() {
        Member memberA = createMember("userA@test.com");
        Member memberB = createMember("userB@test.com");
        MatchRequest reqA = createPendingRequest(memberA, NIGHT_WORK, 35);
        MatchRequest reqB = createPendingRequest(memberB, SLACKING, 35);

        matchRequestService.retryPendingMatches();

        assertThat(matchRequestRepository.findById(reqA.getId()).get().getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(matchRequestRepository.findById(reqB.getId()).get().getStatus()).isEqualTo(MatchStatus.MATCHED);
    }

    @Test
    @DisplayName("retryPendingMatches: 3명 중 한 쌍이 먼저 매칭되면 나머지 1명은 PENDING으로 남는다")
    void t6() {
        Member memberA = createMember("userA@test.com");
        Member memberB = createMember("userB@test.com");
        Member memberC = createMember("userC@test.com");
        MatchRequest reqA = createPendingRequest(memberA, NIGHT_WORK, 20);
        MatchRequest reqB = createPendingRequest(memberB, MEETING_BOMB, 20);
        MatchRequest reqC = createPendingRequest(memberC, MEETING_BOMB, 18);

        matchRequestService.retryPendingMatches();

        MatchStatus statusA = matchRequestRepository.findById(reqA.getId()).get().getStatus();
        MatchStatus statusB = matchRequestRepository.findById(reqB.getId()).get().getStatus();
        MatchStatus statusC = matchRequestRepository.findById(reqC.getId()).get().getStatus();

        assertThat(statusA).isEqualTo(MatchStatus.MATCHED);
        assertThat(statusB).isEqualTo(MatchStatus.MATCHED);
        assertThat(statusC).isEqualTo(MatchStatus.PENDING);
    }
}