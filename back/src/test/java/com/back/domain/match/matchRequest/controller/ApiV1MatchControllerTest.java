package com.back.domain.match.matchRequest.controller;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.member.emailVerification.entity.EmailVerificationToken;
import com.back.domain.member.emailVerification.repository.EmailVerificationTokenRepository;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ApiV1MatchControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    EmailVerificationTokenRepository emailVerificationTokenRepository;

    @BeforeEach
    void setUp() {
        matchRequestRepository.deleteAll();
        memberRepository.deleteAll();
    }

    private void preVerifyEmail(String email) {
        EmailVerificationToken token = new EmailVerificationToken(email, "000000", 10);
        token.markVerified();
        emailVerificationTokenRepository.save(token);
    }

    private String signupAndLogin(String email, String industry) throws Exception {
        preVerifyEmail(email);

        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "1234",
                                    "industry": "%s"
                                }
                                """.formatted(email, industry))
        );

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

        return new ObjectMapper()
                .readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();
    }

    @Test
    @DisplayName("매칭 요청 생성 성공 - 상대 없을 시 PENDING")
    void t1() throws Exception {
        String accessToken = signupAndLogin("user1@test.com", "IT/개발");

        ResultActions resultActions = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.msg").value("매칭 요청 생성 성공"))
                .andExpect(jsonPath("$.data.matchRequestId").exists())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestedAt").exists());
    }

    @Test
    @DisplayName("매칭 요청 생성 성공 - industry + situation 일치 시 MATCHED")
    void t2() throws Exception {
        String accessToken1 = signupAndLogin("user1@test.com", "IT/개발");
        String accessToken2 = signupAndLogin("user2@test.com", "IT/개발");

        mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        );

        ResultActions resultActions = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.status").value("MATCHED"));
    }

    @Test
    @DisplayName("매칭 요청 생성 성공 - industry만 일치 시 create 직후 PENDING (2순위)")
    void t3() throws Exception {
        String accessToken1 = signupAndLogin("user1@test.com", "IT/개발");
        String accessToken2 = signupAndLogin("user2@test.com", "IT/개발");

        mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        );

        ResultActions resultActions = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "상사 억까"
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("이미 진행 중인 매칭 요청이 있을 시 409")
    void t4() throws Exception {
        String accessToken = signupAndLogin("user1@test.com", "IT/개발");

        mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        );

        ResultActions resultActions = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"))
                .andExpect(jsonPath("$.msg").value("이미 진행 중인 매칭 요청이 있습니다."));
    }

    @Test
    @DisplayName("비인증 사용자 매칭 요청 시 401")
    void t5() throws Exception {
        ResultActions resultActions = mvc.perform(
                post("/api/v1/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("situation 없이 매칭 요청 시 400")
    void t6() throws Exception {
        String accessToken = signupAndLogin("user1@test.com", "IT/개발");

        ResultActions resultActions = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": ""
                                }
                                """)
        ).andDo(print());

        resultActions
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("매칭 상태 조회 - PENDING")
    void t7() throws Exception {
        String accessToken = signupAndLogin("user1@test.com", "IT/개발");

        String createResponse = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        ).andReturn().getResponse().getContentAsString();

        String matchRequestId = new ObjectMapper()
                .readTree(createResponse)
                .path("data")
                .path("matchRequestId")
                .asText();

        ResultActions resultActions = mvc.perform(
                get("/api/v1/matches/" + matchRequestId)
                        .header("Authorization", "Bearer " + accessToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-2"))
                .andExpect(jsonPath("$.msg").value("매칭 대기 중"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("매칭 상태 조회 - MATCHED")
    void t8() throws Exception {
        String accessToken1 = signupAndLogin("user1@test.com", "IT/개발");
        String accessToken2 = signupAndLogin("user2@test.com", "IT/개발");

        mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        );

        String createResponse = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "situation": "야근 중"
                                }
                                """)
        ).andReturn().getResponse().getContentAsString();

        String matchRequestId = new ObjectMapper()
                .readTree(createResponse)
                .path("data")
                .path("matchRequestId")
                .asText();

        ResultActions resultActions = mvc.perform(
                get("/api/v1/matches/" + matchRequestId)
                        .header("Authorization", "Bearer " + accessToken2)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("매칭 성공"))
                .andExpect(jsonPath("$.data.status").value("MATCHED"));
    }

    @Test
    @DisplayName("존재하지 않는 matchRequestId 조회 시 404")
    void t9() throws Exception {
        String accessToken = signupAndLogin("user1@test.com", "IT/개발");

        ResultActions resultActions = mvc.perform(
                get("/api/v1/matches/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + accessToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }


    @Test
    @DisplayName("매칭 취소 성공")
    void t10() throws Exception {
        String accessToken = signupAndLogin("user1@test.com", "IT/개발");

        String createResponse = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "situation": "야근 중"
                            }
                            """)
        ).andReturn().getResponse().getContentAsString();

        String matchRequestId = new ObjectMapper()
                .readTree(createResponse)
                .path("data")
                .path("matchRequestId")
                .asText();

        ResultActions resultActions = mvc.perform(
                delete("/api/v1/matches/" + matchRequestId)
                        .header("Authorization", "Bearer " + accessToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("매칭 요청이 취소되었습니다."));
    }

    @Test
    @DisplayName("MATCHED 상태 매칭 취소 시 409")
    void t11() throws Exception {
        String accessToken1 = signupAndLogin("user1@test.com", "IT/개발");
        String accessToken2 = signupAndLogin("user2@test.com", "IT/개발");

        mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "situation": "야근 중"
                            }
                            """)
        );

        String createResponse = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "situation": "야근 중"
                            }
                            """)
        ).andReturn().getResponse().getContentAsString();

        String matchRequestId = new ObjectMapper()
                .readTree(createResponse)
                .path("data")
                .path("matchRequestId")
                .asText();

        ResultActions resultActions = mvc.perform(
                delete("/api/v1/matches/" + matchRequestId)
                        .header("Authorization", "Bearer " + accessToken2)
        ).andDo(print());

        resultActions
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"))
                .andExpect(jsonPath("$.msg").value("이미 매칭된 요청은 취소할 수 없습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 matchRequestId 취소 시 404")
    void t12() throws Exception {
        String accessToken = signupAndLogin("user1@test.com", "IT/개발");

        ResultActions resultActions = mvc.perform(
                delete("/api/v1/matches/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + accessToken)
        ).andDo(print());

        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }

    @Test
    @DisplayName("홈 통계의 상황별 순위는 인원수 내림차순으로 정렬된다 - 상황 랭킹 UI가 이 순서를 그대로 씀")
    void t13() throws Exception {
        // Given - NIGHT_WORK 3명, MEETING_BOMB 2명, BOSS_BLAME 1명 순으로 매칭 완료 상태 생성
        saveMatchedRequest("night1@test.com", Situation.NIGHT_WORK);
        saveMatchedRequest("night2@test.com", Situation.NIGHT_WORK);
        saveMatchedRequest("night3@test.com", Situation.NIGHT_WORK);
        saveMatchedRequest("meeting1@test.com", Situation.MEETING_BOMB);
        saveMatchedRequest("meeting2@test.com", Situation.MEETING_BOMB);
        saveMatchedRequest("boss1@test.com", Situation.BOSS_BLAME);

        // When
        ResultActions resultActions = mvc.perform(get("/api/v1/matches/stats/home")).andDo(print());

        // Then - count 내림차순: 야근 중(3) -> 회의 폭탄(2) -> 상사 억까(1)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.situationStats[0].situation").value("야근 중"))
                .andExpect(jsonPath("$.data.situationStats[0].count").value(3))
                .andExpect(jsonPath("$.data.situationStats[1].situation").value("회의 폭탄"))
                .andExpect(jsonPath("$.data.situationStats[1].count").value(2))
                .andExpect(jsonPath("$.data.situationStats[2].situation").value("상사 억까"))
                .andExpect(jsonPath("$.data.situationStats[2].count").value(1));
    }

    private void saveMatchedRequest(String email, Situation situation) {
        Member member = memberRepository.save(new Member(email, "1234", Industry.IT, "USER"));
        ChatRoom room = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        MatchRequest request = new MatchRequest(member, situation);
        request.matchWith(room);
        matchRequestRepository.save(request);
    }
}
