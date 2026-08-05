package com.back.domain.match.matchRequest.service;

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.MatchStatus;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.match.matchRequest.service.RedisMatchQueue;
import com.back.domain.match.scheduler.MatchScheduler;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.notification.service.MatchNotificationService;
import com.back.global.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.back.domain.match.matchRequest.entity.Situation.*;
import static com.back.domain.member.member.entity.Industry.IT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("test")
@SpringBootTest
public class MatchRequestServiceTest {

    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private MatchScheduler matchScheduler;
    @Autowired
    private RedisMatchQueue redisMatchQueue;
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
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String NOTIFICATION_KEY_PREFIX = "notification:member:";

    private final List<Member> createdMembers = new ArrayList<>();
    // createPendingRequest()로 Redis ZSet에 직접 시딩한 항목들 - 매칭돼서 이미 ZREM된 것도 있고
    // PENDING으로 남은 것도 있어서, 테스트 간 오염 방지를 위해 남은 게 있으면 정리한다 (이미 없는 멤버 제거는 no-op).
    private record QueueEntry(Industry industry, Situation situation, UUID id) {}
    private final List<QueueEntry> queuedEntries = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        queuedEntries.forEach(e -> redisMatchQueue.remove(e.industry(), e.situation(), e.id()));
        queuedEntries.clear();
        matchRequestRepository.deleteAll();
        chatRoomParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        // 알림은 Redis(notification:member:{id})에 쌓이는데 H2와 달리 영속되므로, memberId 재사용 시
        // 이전 실행의 알림과 섞이지 않도록 정리한다.
        createdMembers.forEach(m -> redisTemplate.delete(NOTIFICATION_KEY_PREFIX + m.getId()));
        createdMembers.forEach(memberRepository::delete);
        createdMembers.clear();
    }
    private Member createMember(String email) {
        Member member = memberRepository.save(new Member(email, "1234", IT, "USER"));
        createdMembers.add(member);
        return member;
    }

    // 매칭 후보 조회는 이제 DB가 아니라 Redis ZSet(match:queue:{industry}:{situation})만 보므로,
    // 테스트 요청도 저장과 동시에 실제 create()의 AFTER_COMMIT이 하는 것과 같은 방식으로 ZADD까지 직접 해준다.
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
    @DisplayName("Tier 0: 같은 상황이면 대기 시간에 관계없이 즉시 매칭된다")
    void t1() {
        Member memberA = createMember("userA@test.com");
        Member memberB = createMember("userB@test.com");
        MatchRequest reqA = createPendingRequest(memberA, NIGHT_WORK, 0);
        MatchRequest reqB = createPendingRequest(memberB, NIGHT_WORK, 0);

        matchScheduler.retryPendingMatches();

        assertThat(matchRequestRepository.findById(reqA.getId()).get().getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(matchRequestRepository.findById(reqB.getId()).get().getStatus()).isEqualTo(MatchStatus.MATCHED);

        // 알림은 MatchSuccessEvent -> @Async 리스너로 비동기 처리되므로 폴링으로 대기한다.
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(matchNotificationService.getNotifications(memberA.getId(), null)).hasSize(1));
    }

    @Test
    @DisplayName("Tier 1: 상황이 다르고 15초가 지나지 않으면 매칭되지 않는다")
    void t2() {
        Member memberA = createMember("userA@test.com");
        Member memberB = createMember("userB@test.com");
        MatchRequest reqA = createPendingRequest(memberA, NIGHT_WORK, 10);
        MatchRequest reqB = createPendingRequest(memberB, MEETING_BOMB, 10);

        matchScheduler.retryPendingMatches();

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

        matchScheduler.retryPendingMatches();

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

        matchScheduler.retryPendingMatches();

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

        matchScheduler.retryPendingMatches();

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

        matchScheduler.retryPendingMatches();

        MatchStatus statusA = matchRequestRepository.findById(reqA.getId()).get().getStatus();
        MatchStatus statusB = matchRequestRepository.findById(reqB.getId()).get().getStatus();
        MatchStatus statusC = matchRequestRepository.findById(reqC.getId()).get().getStatus();

        assertThat(statusA).isEqualTo(MatchStatus.MATCHED);
        assertThat(statusB).isEqualTo(MatchStatus.MATCHED);
        assertThat(statusC).isEqualTo(MatchStatus.PENDING);
    }

    @Test
    @DisplayName("cancel(): 취소 시점에 이미 MATCHED로 확정된 요청은 취소가 거부되고, row도 지워지지 않는다")
    void t7_이미매칭된요청은_취소해도_삭제되지않는다() {
        // Given - 매칭 배치가 먼저 두 요청을 확정시킨 상황을 재현 (cancel과 processMatch가
        // 동시에 같은 요청을 두고 경합하다가, cancel의 상태 체크 이후에 매칭이 확정된 경우와 동치)
        Member memberA = createMember("cancelRaceA@test.com");
        Member memberB = createMember("cancelRaceB@test.com");
        MatchRequest reqA = createPendingRequest(memberA, NIGHT_WORK, 0);
        createPendingRequest(memberB, NIGHT_WORK, 0);

        matchScheduler.retryPendingMatches();
        // 실제 컨트롤러 흐름은 OSIV로 요청 전체에서 세션이 열려있어 lazy 필드(member) 접근이
        // 문제없지만, 이 테스트엔 그 세션이 없으므로 member까지 즉시 로딩해서 가져온다.
        MatchRequest matchedA = matchRequestRepository.findByIdWithMember(reqA.getId()).orElseThrow();
        assertThat(matchedA.getStatus()).isEqualTo(MatchStatus.MATCHED);

        // When / Then - 이미 MATCHED인 요청을 취소하면 실패해야 하고,
        assertThatThrownBy(() -> matchRequestService.cancel(matchedA, memberA))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("이미 매칭된 요청은 취소할 수 없습니다");

        // row 자체는 삭제되지 않고 MATCHED 상태로 그대로 남아있어야 한다 (버그 재현 시나리오에선
        // 여기서 row가 삭제돼버려서, 채팅방은 만들어졌는데 본인 MatchRequest만 사라졌었다).
        assertThat(matchRequestRepository.findById(reqA.getId())).isPresent();
        assertThat(matchRequestRepository.findById(reqA.getId()).get().getStatus()).isEqualTo(MatchStatus.MATCHED);
    }
}