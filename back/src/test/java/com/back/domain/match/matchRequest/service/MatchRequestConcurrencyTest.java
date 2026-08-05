package com.back.domain.match.matchRequest.service;

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.match.matchRequest.service.RedisMatchQueue;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 진짜 동시 요청을 재현해야 해서 @Transactional을 안 쓴다 (스레드별로 각자 트랜잭션이 필요함).
// 그래서 정리도 수동으로 한다.
@ActiveProfiles("test")
@SpringBootTest
class MatchRequestConcurrencyTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private RedisMatchQueue redisMatchQueue;
    @Autowired
    private MatchRequestRepository matchRequestRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;

    private final List<Member> createdMembers = new ArrayList<>();
    // createPendingRequest()로 Redis ZSet에 직접 시딩한 항목들 - 매칭 성공 시 이미 ZREM됐을 수도 있어서
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

    // 팀의 3단계 매칭 알고리즘(Tier0/1/2가 elapsed 시간에 따라 후보 범위를 넓힘)을 고려해서,
    // 이미 산업군 전체 매칭(Tier2, 30초 이상)이 적용되는 시점으로 미리 만들어둔다.
    // 그래야 상황(situation)이 서로 달라도 후보로 잡혀서 실제 매칭 시도가 일어난다.
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
    @DisplayName("동시에 두 명이 같은 후보를 노려도, 후보는 채팅방 딱 하나에만 들어간다 (비관적 락)")
    void 동시_요청_중복매칭_방지() throws Exception {
        // Given - 공용 후보 하나 + 경쟁할 두 유저, 셋 다 Tier2(산업군 전체 매칭) 구간인 35초 전 요청으로 세팅.
        // userA, userB도 서로 후보가 될 수 있어서 "누가 후보랑 매칭되는지"는 실행마다 달라질 수 있음(정상).
        // 그래서 "특정 조합으로 매칭됐는지"가 아니라 "후보가 방 2개에 동시에 들어가는 사고가 없는지"를 검증한다.
        Member candidate = memberService.joinWithoutEmailVerification("race_candidate@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(candidate);
        createPendingRequest(candidate, Situation.OTHER, 35);

        Member userA = memberService.joinWithoutEmailVerification("race_a@test.com", "1234", Industry.IT, "USER");
        Member userB = memberService.joinWithoutEmailVerification("race_b@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(userA);
        createdMembers.add(userB);
        MatchRequest requestA = createPendingRequest(userA, Situation.NIGHT_WORK, 35);
        MatchRequest requestB = createPendingRequest(userB, Situation.MEETING_BOMB, 35);

        // 두 스레드가 정확히 같은 시점에 출발하도록 래치로 맞춤
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> futureA = executor.submit(() -> {
            ready.countDown();
            start.await();
            matchRequestService.tryMatch(requestA.getUuid());
            return null;
        });
        Future<?> futureB = executor.submit(() -> {
            ready.countDown();
            start.await();
            matchRequestService.tryMatch(requestB.getUuid());
            return null;
        });

        ready.await();
        start.countDown();

        futureA.get(10, TimeUnit.SECONDS);
        futureB.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then - 락이 없었다면 두 트랜잭션이 동시에 candidate를 "매칭 가능"으로 보고
        // 각자 채팅방을 만들어 candidate를 참여자로 추가했을 수 있다 (중복 참여).
        // 락이 제대로 걸렸다면 candidate는 정확히 방 하나에만 참여자로 들어가 있어야 한다.
        long candidateParticipations = chatRoomParticipantRepository.findAll().stream()
                .filter(p -> p.getMember().getId().equals(candidate.getId()))
                .count();

        assertThat(candidateParticipations).isEqualTo(1);
    }
}