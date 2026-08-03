package com.back.domain.dashboard.dashboard.controller;

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.match.matchRequest.repository.MatchingOutboxRepository;
import com.back.domain.match.matchRequest.service.RedisMatchQueue;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.member.service.MemberService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
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
    private MockMvc mvc;
    @Autowired
    private MemberRepository memberRepository;
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
        createdMembers.add(memberService.joinWithoutEmailVerification("dashboard1@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER"));
        createdMembers.add(memberService.joinWithoutEmailVerification("dashboard2@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER"));

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
        createdMembers.add(memberService.joinWithoutEmailVerification("normaluser@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER"));
        String userToken = loginAndGetToken("normaluser@test.com");

        mvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"));
    }
}