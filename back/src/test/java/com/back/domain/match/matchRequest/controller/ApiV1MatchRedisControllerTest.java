package com.back.domain.match.matchRequest.controller;

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.MatchStatus;
import com.back.domain.match.matchRequest.entity.MatchingOutbox;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.match.matchRequest.repository.MatchingOutboxRepository;
import com.back.domain.match.matchRequest.service.MatchRequestService;
import com.back.domain.match.matchRequest.service.RedisMatchQueue;
import com.back.domain.match.scheduler.MatchScheduler;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.member.service.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 매칭 대기열에 Redis를 도입하면서 새로 생긴, 순수 매칭 알고리즘 테스트로는 커버되지 않는
 * "Redis 배관(plumbing)" 자체의 동작(적재/장애/재시도/스캔 최적화/셀프매칭 배제)을 검증한다.
 * ApiV1ChatMessageRedisControllerTest와 동일한 성격의 전용 파일이다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ApiV1MatchRedisControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MatchRequestRepository matchRequestRepository;
    @Autowired
    private MatchingOutboxRepository matchingOutboxRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private MatchScheduler matchScheduler;

    @MockitoSpyBean
    private RedisTemplate<String, String> redisTemplate;
    @MockitoSpyBean
    private RedisMatchQueue redisMatchQueue;

    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;

    private final List<Member> createdMembers = new ArrayList<>();

    // matchRequestRepository에 대응하는 행이 없는(유령) Redis 항목 등, DB 스캔으로는 못 찾는
    // 잔여 큐 데이터를 정리하기 위한 별도 추적 목록.
    private record QueueEntry(Industry industry, Situation situation, UUID id) {}
    private final List<QueueEntry> extraQueueEntries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        boolean isRedisAvailable = false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redisHost, redisPort), 1000);
            isRedisAvailable = socket.isConnected();
        } catch (Exception ignored) {
        }
        Assumptions.assumeTrue(isRedisAvailable, "로컬 Redis 서버가 꺼져 있어 매칭 Redis 통합 테스트를 스킵합니다.");

        // 스파이 빈은 테스트 클래스 인스턴스 전체에서 재사용되므로, 이전 테스트의 스텁/호출 이력이
        // 남아있으면 verify()/when() 결과가 오염된다. 매 테스트 시작 시 반드시 초기화한다.
        reset(redisTemplate);
        reset(redisMatchQueue);
    }

    // ApiV1MatchControllerTest와 동일한 정리 패턴: TestTransaction으로 강제 커밋한 데이터는
    // 기본 롤백으로 안 지워지므로, 트랜잭션을 새로 열어 수동으로 치우고 그 정리 자체도 커밋한다.
    @AfterEach
    void cleanUp() {
        if (TestTransaction.isActive()) {
            TestTransaction.end();
        }
        TestTransaction.start();

        // 정리 작업 자체는 실제 Redis를 타야 하므로, 테스트가 걸어둔 장애 스텁을 먼저 걷어낸다.
        reset(redisTemplate);
        reset(redisMatchQueue);

        matchRequestRepository.findAll().forEach(mr ->
                redisMatchQueue.remove(mr.getIndustry(), mr.getSituation(), mr.getUuid()));
        extraQueueEntries.forEach(e -> redisMatchQueue.remove(e.industry(), e.situation(), e.id()));
        extraQueueEntries.clear();

        matchRequestRepository.deleteAll();
        matchingOutboxRepository.deleteAll();
        chatRoomParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        createdMembers.forEach(memberRepository::delete);
        createdMembers.clear();

        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @Test
    @DisplayName("[정상] 매칭 요청 생성 시 Redis ZSet(match:queue:{industry}:{situation})에 정확히 적재된다")
    void t1_생성시_ZADD_적재() throws Exception {
        Member member = memberService.joinWithoutEmailVerification("redis_match1@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(member);
        String accessToken = memberService.genAccessToken(member);

        String createResponse = mvc.perform(post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "situation": "야근 중" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // AFTER_COMMIT 트리거를 위해 테스트 트랜잭션을 강제 커밋한다.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        String matchRequestId = new ObjectMapper().readTree(createResponse).path("data").path("matchRequestId").asText();

        Set<String> members = redisTemplate.opsForZSet().range("match:queue:IT:NIGHT_WORK", 0, -1);
        assertThat(members).containsExactly(matchRequestId);
    }

    @Test
    @DisplayName("[정상] 매칭 취소 시 ZREM되고, 대기열이 비면 Redis가 키를 자동 삭제한다")
    void t2_취소시_ZREM_및_자동삭제() throws Exception {
        Member member = memberService.joinWithoutEmailVerification("redis_match2@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(member);
        String accessToken = memberService.genAccessToken(member);

        String createResponse = mvc.perform(post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "situation": "야근 중" }
                                """))
                .andReturn().getResponse().getContentAsString();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        String matchRequestId = new ObjectMapper().readTree(createResponse).path("data").path("matchRequestId").asText();
        String key = "match:queue:IT:NIGHT_WORK";
        assertThat(redisTemplate.hasKey(key)).isTrue();

        mvc.perform(delete("/api/v1/matches/" + matchRequestId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    @DisplayName("[장애] Redis ZADD가 실패해도 매칭 신청 API는 201/PENDING으로 성공하고, 아웃박스는 FAIL로 마킹된다")
    void t3_레디스장애시에도_매칭신청은_성공() throws Exception {
        Member member = memberService.joinWithoutEmailVerification("redis_match3@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(member);
        String accessToken = memberService.genAccessToken(member);

        // Redis ZADD 시점에 통신 장애가 발생하는 것처럼 모킹
        ZSetOperations<String, String> mockZSet = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(mockZSet);
        when(mockZSet.add(anyString(), anyString(), anyDouble()))
                .thenThrow(new RedisConnectionFailureException("Redis Connection Refused"));

        String createResponse = mvc.perform(post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "situation": "야근 중" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        UUID matchRequestId = UUID.fromString(
                new ObjectMapper().readTree(createResponse).path("data").path("matchRequestId").asText());

        assertThat(matchRequestRepository.findByUuid(matchRequestId)).isNotNull();

        MatchingOutbox outbox = matchingOutboxRepository.findAll().stream()
                .filter(o -> o.getMatchRequestId().equals(matchRequestId))
                .findFirst().orElseThrow();
        assertThat(outbox.getStatus()).isEqualTo(MatchingOutbox.OutboxStatus.FAIL);
    }

    @Test
    @DisplayName("[재시도] FAIL 상태 아웃박스는 재시도 스케줄러가 재적재하여 SUCCESS로 바꾼다")
    void t4_아웃박스_재시도_성공() {
        Member member = memberService.joinWithoutEmailVerification("redis_match4@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(member);

        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(member, Situation.NIGHT_WORK));
        long epochMilli = matchRequest.getRequestedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        MatchingOutbox outbox = MatchingOutbox.create(matchRequest.getUuid(), Industry.IT, Situation.NIGHT_WORK, epochMilli);
        outbox.markFailed();
        matchingOutboxRepository.save(outbox);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        matchScheduler.retryOutboxEvents();

        MatchingOutbox refreshed = matchingOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(MatchingOutbox.OutboxStatus.SUCCESS);
        assertThat(redisTemplate.opsForZSet().range("match:queue:IT:NIGHT_WORK", 0, -1))
                .contains(matchRequest.getUuid().toString());
    }

    @Test
    @DisplayName("[재시도 상한] retryCount가 5 이상인 아웃박스는 더 이상 재시도하지 않는다")
    void t5_재시도_상한_초과시_스킵() {
        Member member = memberService.joinWithoutEmailVerification("redis_match5@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(member);

        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(member, Situation.NIGHT_WORK));
        long epochMilli = matchRequest.getRequestedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        MatchingOutbox outbox = MatchingOutbox.create(matchRequest.getUuid(), Industry.IT, Situation.NIGHT_WORK, epochMilli);
        for (int i = 0; i < 5; i++) {
            outbox.markFailed();
        }
        matchingOutboxRepository.save(outbox);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        matchScheduler.retryOutboxEvents();

        MatchingOutbox refreshed = matchingOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(MatchingOutbox.OutboxStatus.FAIL);
        assertThat(refreshed.getRetryCount()).isEqualTo(5);
        assertThat(redisTemplate.hasKey("match:queue:IT:NIGHT_WORK")).isFalse();
    }

    @Test
    @DisplayName("[최적화] 대기열이 비어있는 산업군×상황 조합은 getAllIds 조회 자체를 건너뛴다")
    void t6_빈대기열은_스캔을_건너뛴다() {
        Member member = memberService.joinWithoutEmailVerification("redis_match6@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(member);

        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(member, Situation.NIGHT_WORK));
        redisMatchQueue.add(Industry.IT, Situation.NIGHT_WORK, matchRequest.getUuid(), System.currentTimeMillis());
        extraQueueEntries.add(new QueueEntry(Industry.IT, Situation.NIGHT_WORK, matchRequest.getUuid()));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        matchScheduler.retryPendingMatches();

        verify(redisMatchQueue, atLeastOnce()).getAllIds(Industry.IT, Situation.NIGHT_WORK);
        verify(redisMatchQueue, never()).getAllIds(Industry.OFFICE, Situation.BOSS_BLAME);
    }

    @Test
    @DisplayName("[복원력] 대기열에 DB에 없는 유령 ID가 섞여 있어도 나머지 대기자는 정상적으로 매칭 처리된다")
    void t7_유령ID가_있어도_나머지는_정상매칭() {
        Member memberA = memberService.joinWithoutEmailVerification("redis_match7a@test.com", "1234", Industry.IT, "USER");
        Member memberB = memberService.joinWithoutEmailVerification("redis_match7b@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(memberA);
        createdMembers.add(memberB);

        MatchRequest reqA = matchRequestRepository.save(new MatchRequest(memberA, Situation.NIGHT_WORK));
        MatchRequest reqB = matchRequestRepository.save(new MatchRequest(memberB, Situation.NIGHT_WORK));

        long now = System.currentTimeMillis();
        redisMatchQueue.add(Industry.IT, Situation.NIGHT_WORK, reqA.getUuid(), now);
        redisMatchQueue.add(Industry.IT, Situation.NIGHT_WORK, reqB.getUuid(), now + 10);

        // DB에 대응하는 행이 없는 유령 ID. 점수를 훨씬 미래로 줘서 getOldest()의 "1등" 후보로는
        // 절대 뽑히지 않지만(그러면 reqA/reqB 매칭 자체가 막힘), getAllIds() 전체 스캔 목록에는
        // 포함되어 스케줄러가 이 ID로 직접 tryMatch를 시도하다 404(존재하지 않음)로 실패하게 만든다.
        UUID phantomId = UUID.randomUUID();
        redisMatchQueue.add(Industry.IT, Situation.NIGHT_WORK, phantomId, now + 999_999);
        extraQueueEntries.add(new QueueEntry(Industry.IT, Situation.NIGHT_WORK, phantomId));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 유령 ID 처리 중 예외가 나더라도 스케줄러 루프 전체가 죽지 않고 나머지를 계속 처리해야 한다.
        matchScheduler.retryPendingMatches();

        MatchRequest refreshedA = matchRequestRepository.findById(reqA.getId()).orElseThrow();
        MatchRequest refreshedB = matchRequestRepository.findById(reqB.getId()).orElseThrow();
        assertThat(refreshedA.getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(refreshedB.getStatus()).isEqualTo(MatchStatus.MATCHED);
    }

    @Test
    @DisplayName("[셀프매칭 배제] 대기열에 혼자 있을 때는 getOldest()가 자기 자신을 반환하지만 매칭되지 않고 대기열에 남는다")
    void t8_혼자있으면_셀프매칭_안됨() {
        Member member = memberService.joinWithoutEmailVerification("redis_match8@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(member);

        MatchRequest matchRequest = matchRequestRepository.save(new MatchRequest(member, Situation.NIGHT_WORK));
        redisMatchQueue.add(Industry.IT, Situation.NIGHT_WORK, matchRequest.getUuid(), System.currentTimeMillis());

        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 대기열에 나 혼자뿐이라 getOldestCandidates()는 나 자신을 반환한다 - 셀프 매칭 배제 필터가 필요한 이유다.
        assertThat(redisMatchQueue.getOldestCandidates(Industry.IT, Situation.NIGHT_WORK, 20)).contains(matchRequest.getUuid());

        matchRequestService.tryMatch(matchRequest.getUuid());

        MatchRequest refreshed = matchRequestRepository.findById(matchRequest.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(MatchStatus.PENDING);
        assertThat(redisMatchQueue.getOldestCandidates(Industry.IT, Situation.NIGHT_WORK, 20)).contains(matchRequest.getUuid());
    }
}
