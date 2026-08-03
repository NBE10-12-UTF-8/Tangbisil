package com.back.domain.dashboard.dashboard.controller;

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.match.matchRequest.repository.MatchingOutboxRepository;
import com.back.domain.match.matchRequest.service.RedisMatchQueue;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.member.service.MemberService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.back.domain.member.member.entity.Industry.IT;
import static com.back.domain.member.member.entity.Industry.OFFICE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ApiV1DashboardControllerTest {
    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private MatchRequestRepository matchRequestRepository;
    @Autowired
    private MatchingOutboxRepository matchingOutboxRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private RedisMatchQueue redisMatchQueue;

    private final List<Member> createdMembers = new ArrayList<>();

    // t2가 TestTransaction.flagForCommit()+end()로 실제 커밋하므로, 기본 롤백만으로는 정리가 안 된다.
    // (ApiV1MatchControllerTest에서 쓴 것과 동일한 정리 패턴)
    @AfterEach
    void cleanUp() {
        if (TestTransaction.isActive()) {
            TestTransaction.end();
        }
        TestTransaction.start();
        matchRequestRepository.findAll().forEach(mr ->
                redisMatchQueue.remove(mr.getIndustry(), mr.getSituation(), mr.getId()));
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
    @DisplayName("관리자 대시보드 통계 조회")
    void t1() throws Exception {
        // Given - 관리자 로그인
        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                             "email": "admin@test.com",
                                             "password": "1234"
                                        }
                                        """)
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/dashboard")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("대시보드 통계 조회 성공"))
                .andExpect(jsonPath("$.data.matchStatistics.totalMembers").exists())
                .andExpect(jsonPath("$.data.matchStatistics.todayMatches").exists())
                .andExpect(jsonPath("$.data.matchStatistics.activeChatRooms").exists())
                .andExpect(jsonPath("$.data.industryStatistics").isArray())
                .andExpect(jsonPath("$.data.recentMatchLogs").isArray());
    }

    @Test
    @DisplayName("관리자 대시보드 - 최근 매칭 로그, 매칭 성사 시 1건만 기록 (중복 제거)")
    void t2() throws Exception {
        // Given - 같은 산업군 유저 두 명이 매칭 성사
        createdMembers.add(memberService.joinWithoutEmailVerification("dashboard1@test.com", "1234", IT, "USER"));
        createdMembers.add(memberService.joinWithoutEmailVerification("dashboard2@test.com", "1234", IT, "USER"));

        String token1 = loginAndGetToken("dashboard1@test.com");
        String token2 = loginAndGetToken("dashboard2@test.com");

        String situationBody = """
                {
                    "situation": "야근 중"
                }
                """;

        mvc.perform(post("/api/v1/matches")
                .header("Authorization", "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(situationBody));

        mvc.perform(post("/api/v1/matches")
                .header("Authorization", "Bearer " + token2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(situationBody));

        String adminToken = loginAndGetToken("admin@test.com");

        // 매칭은 트랜잭션 커밋 이후 AFTER_COMMIT 핸들러에서 비동기로 처리되므로,
        // 테스트 트랜잭션을 강제 커밋해야 그 흐름이 실제로 발화한다.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // Then - 매칭 참여자가 둘이라 MatchRequest는 2건 생기지만, 같은 room이라 로그엔 1건만 잡혀야 함
        // 비동기 매칭이 끝날 때까지 대시보드 조회를 폴링으로 반복한다.
        await().atMost(5, SECONDS).untilAsserted(() ->
                mvc.perform(
                                get("/api/v1/admin/dashboard")
                                        .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.recentMatchLogs[0].industry").value("IT/개발"))
                        .andExpect(jsonPath("$.data.recentMatchLogs[0].situation").value("야근 중"))
                        .andExpect(jsonPath("$.data.recentMatchLogs[0].matchedAt").exists()));
    }

    private String loginAndGetToken(String email) throws Exception {
        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                             "email": "%s",
                                             "password": "1234"
                                        }
                                        """.formatted(email))
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();
    }

    @Test
    @DisplayName("일반 유저는 관리자 대시보드 접근 불가 - 403")
    void t3() throws Exception {
        createdMembers.add(memberService.joinWithoutEmailVerification("normaluser@test.com", "1234", IT, "USER"));
        String userToken = loginAndGetToken("normaluser@test.com");

        mvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"));
    }

    @Test
    @DisplayName("기간별 산업군 가입 통계 조회 - 기간 내 가입한 회원만 산업군별로 집계된다")
    void t4() throws Exception {
        // Given - 조회 기간(2020-01-01~2020-01-31) 안에 IT 2명, 사무업 1명 가입
        Member it1 = memberService.joinWithoutEmailVerification("signup1@test.com", "1234", IT, "USER");
        Member it2 = memberService.joinWithoutEmailVerification("signup2@test.com", "1234", IT, "USER");
        Member office1 = memberService.joinWithoutEmailVerification("signup3@test.com", "1234", OFFICE, "USER");
        backdateCreatedAt(it1, LocalDateTime.of(2020, 1, 10, 0, 0));
        backdateCreatedAt(it2, LocalDateTime.of(2020, 1, 20, 0, 0));
        backdateCreatedAt(office1, LocalDateTime.of(2020, 1, 15, 0, 0));

        // 기간 밖(2020-02-01) 가입자 - 집계에서 제외되어야 함
        Member outOfRange = memberService.joinWithoutEmailVerification("signup4@test.com", "1234", IT, "USER");
        backdateCreatedAt(outOfRange, LocalDateTime.of(2020, 2, 1, 0, 0));

        createdMembers.addAll(List.of(it1, it2, office1, outOfRange));


        String adminToken = loginAndGetToken("admin@test.com");

        // When
        String response = mvc.perform(
                        get("/api/v1/admin/dashboard/industry-signups")
                                .param("startDate", "2020-01-01")
                                .param("endDate", "2020-01-31")
                                .header("Authorization", "Bearer " + adminToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        JsonNode data = new ObjectMapper().readTree(response).path("data");
        assertThat(data.path("startDate").asText()).isEqualTo("2020-01-01");
        assertThat(data.path("endDate").asText()).isEqualTo("2020-01-31");

        Map<String, Integer> counts = new HashMap<>();
        data.path("industryStatistics").forEach(node -> counts.put(node.path("industry").asText(), node.path("count").asInt()));

        assertThat(counts.get("IT/개발")).isEqualTo(2);
        assertThat(counts.get("사무업")).isEqualTo(1);
        assertThat(counts.containsKey("금융업")).isFalse(); // 기간 밖 가입자만 있던 산업군은 아예 안 나와야 함
    }

    @Test
    @DisplayName("기간별 산업군 가입 통계 조회 - 시작일이 종료일보다 늦으면 400")
    void t5() throws Exception {
        String adminToken = loginAndGetToken("admin@test.com");

        mvc.perform(
                        get("/api/v1/admin/dashboard/industry-signups")
                                .param("startDate", "2020-02-01")
                                .param("endDate", "2020-01-01")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }

    @Test
    @DisplayName("기간별 산업군 가입 통계 조회 - 일반 유저는 접근 불가")
    void t6() throws Exception {
        createdMembers.add(memberService.joinWithoutEmailVerification("normaluser2@test.com", "1234", IT, "USER"));
        String userToken = loginAndGetToken("normaluser2@test.com");

        mvc.perform(get("/api/v1/admin/dashboard/industry-signups")
                        .param("startDate", "2020-01-01")
                        .param("endDate", "2020-01-31")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"));
    }

    // @CreatedDate는 최초 저장 시점에만 채워지고 이후 save()로는 갱신 안 되므로,
    // 가입일을 과거로 되돌려 기간 필터링을 검증하는 용도로만 씀
    private void backdateCreatedAt(Member member, LocalDateTime createdAt) {
        ReflectionTestUtils.setField(member, "createdAt", createdAt);
        memberRepository.save(member);
    }

    @Test
    @DisplayName("기간별 산업군 가입 통계 조회 - 파라미터 누락 시 400")
    void t7() throws Exception {
        String adminToken = loginAndGetToken("admin@test.com");

        mvc.perform(get("/api/v1/admin/dashboard/industry-signups")
                        .param("startDate", "2020-01-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }
}