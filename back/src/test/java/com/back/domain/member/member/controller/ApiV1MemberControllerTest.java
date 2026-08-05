package com.back.domain.member.member.controller;

import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.match.matchRequest.repository.MatchingOutboxRepository;
import com.back.domain.match.matchRequest.service.RedisMatchQueue;
import com.back.domain.member.emailVerification.entity.EmailVerificationToken;
import com.back.domain.member.emailVerification.repository.EmailVerificationTokenRepository;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.member.service.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.back.domain.member.member.entity.Industry.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ApiV1MemberControllerTest {
    @Autowired
    private MemberService memberService;
    @Autowired
    private MockMvc mvc;

    @Autowired
    EmailVerificationTokenRepository emailVerificationTokenRepository;

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
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // t9만 TestTransaction.flagForCommit()+end()로 실제 커밋한다. 그 데이터는 기본 롤백으로 안 지워지므로 수동 정리한다.
    // (ApiV1MatchControllerTest에서 쓴 것과 동일한 정리 패턴)
    private final List<Member> createdMembers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        if (TestTransaction.isActive()) {
            TestTransaction.end();
        }
        TestTransaction.start();
        matchRequestRepository.findAll().forEach(mr ->
                redisMatchQueue.remove(mr.getIndustry(), mr.getSituation(), mr.getUuid()));
        matchRequestRepository.deleteAll();
        matchingOutboxRepository.deleteAll();
        chatRoomParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        createdMembers.forEach(memberRepository::delete);
        createdMembers.clear();
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @AfterEach
    void cleanUpLoginAttempts() {
        redisTemplate.delete("login:attempts:brute-force-test@test.com");
        redisTemplate.delete("login:attempts:reset-test@test.com");
    }

    private void preVerifyEmail(String email) {
        EmailVerificationToken token = new EmailVerificationToken(email, "000000", 10);
        token.markVerified();
        emailVerificationTokenRepository.save(token);
    }

    @Test
    @DisplayName("회원가입")
    void t1() throws Exception {
        preVerifyEmail("test@test.com");

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                             "email": "test@test.com",
                                             "password": "1234",
                                             "industry": "IT/개발",
                                             "agreedToTerms": true
                                        }
                                        """)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.msg").value("회원 생성 성공"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.industry").value("IT/개발"));
    }

    @Test
    @DisplayName("로그인")
    void t2() throws Exception {
        preVerifyEmail("test@test.com");

        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                     "email": "test@test.com",
                                     "password": "1234",
                                     "industry": "IT/개발",
                                     "agreedToTerms": true
                                }
                                """)
        );

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                             "email": "test@test.com",
                                             "password": "1234"
                                        }
                                        """)
                )
                .andDo(print());

        // Then - 토큰은 응답 바디가 아니라 HttpOnly 쿠키로만 내려간다
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("로그인 생성 성공"))
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.industry").value("IT/개발"))
                .andExpect(cookie().exists("accessToken"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("accessToken", true))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    @DisplayName("유효하지 않은 이메일 형식으로 회원가입 시 실패")
    void t3() throws Exception {
        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                             "email": "invalid-email-format",
                                             "password": "1234",
                                             "industry": "IT/개발",
                                             "agreedToTerms": true
                                        }
                                        """)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가입되지 않은 이메일로 로그인 시 실패")
    void t4() throws Exception {
        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                             "email": "nonexistent@test.com",
                                             "password": "1234"
                                        }
                                        """)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("로그아웃")
    void t5() throws Exception {
        preVerifyEmail("test@test.com");

        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발",
                                 "agreedToTerms": true
                            }
                            """)
        );

        // Given - 로그인 선행 (토큰은 응답 바디가 아니라 쿠키로 내려온다)
        Cookie accessTokenCookie = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "test@test.com",
                                         "password": "1234"
                                    }
                                    """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken");

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/logout")
                                .cookie(accessTokenCookie)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("로그아웃 생성 성공"));
    }
    @Test
    @DisplayName("내 정보 조회")
    void t6() throws Exception {
        preVerifyEmail("test@test.com");

        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발",
                                 "agreedToTerms": true
                            }
                            """)
        );

        // Given - 로그인 선행
        Cookie accessTokenCookie = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "test@test.com",
                                         "password": "1234"
                                    }
                                    """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken");

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/members/me")
                                .cookie(accessTokenCookie)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("내 정보 조회 성공"))
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.industry").value("IT/개발"));
    }
    @Test
    @DisplayName("산업군 수정")
    void t7() throws Exception {
        preVerifyEmail("test@test.com");

        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발",
                                 "agreedToTerms": true
                            }
                            """)
        );

        // Given - 로그인 선행 (토큰은 응답 바디가 아니라 쿠키로 내려온다)
        Cookie accessTokenCookie = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "test@test.com",
                                         "password": "1234"
                                    }
                                    """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken");

        // When
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/members/me")
                                .cookie(accessTokenCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "industry": "금융업"
                                    }
                                    """)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("소속 산업군 수정 성공"))
                .andExpect(jsonPath("$.data.industry").value("금융업"));
    }
    @Test
    @DisplayName("매칭 이력 조회 성공 - CLOSED 채팅방만 반환")
    void t9() throws Exception {
        // Given - 두 유저 직접 생성 후 매칭
        Member member1 = memberService.joinWithoutEmailVerification("history1@test.com", "1234", IT, "USER");
        Member member2 = memberService.joinWithoutEmailVerification("history2@test.com", "1234", IT, "USER");
        createdMembers.add(member1);
        createdMembers.add(member2);
        String accessToken1 = memberService.genAccessToken(member1);
        String accessToken2 = memberService.genAccessToken(member2);

        mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "situation": "야근 중" }
                                """)
        );

        String matchResponse = mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "situation": "야근 중" }
                                """)
        ).andReturn().getResponse().getContentAsString();

        String matchRequestId = new ObjectMapper().readTree(matchResponse).path("data").path("matchRequestId").asText();

        // 매칭은 트랜잭션 커밋 이후 AFTER_COMMIT 핸들러에서 비동기로 처리되므로,
        // 테스트 트랜잭션을 강제 커밋하고 실제로 MATCHED(+채팅방 생성)될 때까지 폴링으로 기다린다.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        AtomicReference<String> roomIdRef = new AtomicReference<>();
        await().atMost(5, SECONDS).untilAsserted(() -> {
            String statusResponse = mvc.perform(
                    get("/api/v1/matches/" + matchRequestId)
                            .header("Authorization", "Bearer " + accessToken2)
            ).andReturn().getResponse().getContentAsString();

            String roomId = new ObjectMapper().readTree(statusResponse).path("data").path("chatRoomId").asText();
            assertThat(roomId).isNotBlank();
            roomIdRef.set(roomId);
        });
        String roomId = roomIdRef.get();

        // Given - 채팅방 종료
        mvc.perform(
                patch("/api/v1/rooms/" + roomId)
                        .header("Authorization", "Bearer " + accessToken1)
        );

        // When
        ResultActions resultActions = mvc.perform(
                get("/api/v1/members/me/matches")
                        .header("Authorization", "Bearer " + accessToken1)
        ).andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].industry").value("IT/개발"))
                .andExpect(jsonPath("$.data[0].situation").value("야근 중"))
                .andExpect(jsonPath("$.data[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.data[0].matchedAt").exists());
    }

    @Test
    @DisplayName("매칭 이력 없을 때 빈 배열 반환")
    void t10() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("history3@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        // When
        ResultActions resultActions = mvc.perform(
                get("/api/v1/members/me/matches")
                        .header("Authorization", "Bearer " + accessToken)
        ).andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("ACTIVE 채팅방은 이력에 포함되지 않음")
    void t11() throws Exception {
        // Given - 매칭 후 채팅방 종료 안 함
        Member member1 = memberService.joinWithoutEmailVerification("history4@test.com", "1234", IT, "USER");
        Member member2 = memberService.joinWithoutEmailVerification("history5@test.com", "1234", IT, "USER");
        String accessToken1 = memberService.genAccessToken(member1);
        String accessToken2 = memberService.genAccessToken(member2);

        mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "situation": "야근 중" }
                                """)
        );

        mvc.perform(
                post("/api/v1/matches")
                        .header("Authorization", "Bearer " + accessToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "situation": "야근 중" }
                                """)
        );

        // When - 채팅방 종료 없이 바로 이력 조회
        ResultActions resultActions = mvc.perform(
                get("/api/v1/members/me/matches")
                        .header("Authorization", "Bearer " + accessToken1)
        ).andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("비인증 사용자 매칭 이력 조회 시 401")
    void t12() throws Exception {
        // When
        ResultActions resultActions = mvc.perform(
                get("/api/v1/members/me/matches")
        ).andDo(print());

        // Then
        resultActions
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원 탈퇴")
    void t8() throws Exception {
        preVerifyEmail("test@test.com");

        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발",
                                 "agreedToTerms": true
                            }
                            """)
        );

        // Given - 로그인 선행 (토큰은 응답 바디가 아니라 쿠키로 내려온다)
        Cookie accessTokenCookie = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "test@test.com",
                                         "password": "1234"
                                    }
                                    """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken");

        // When
        ResultActions resultActions = mvc
                .perform(
                        delete("/api/v1/members/me")
                                .cookie(accessTokenCookie)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("회원 삭제 성공"));
    }
    @Test
    @DisplayName("AccessToken 재발급 성공")
    void t13() throws Exception {
        // given
        Member member = memberService.joinWithoutEmailVerification(
                "refresh@test.com",
                "1234",
                IT,
                "USER"
        );

        UUID refreshToken = memberService.genRefreshToken(member);

        Cookie cookie = new Cookie(
                "refreshToken",
                refreshToken.toString()
        );

        // when
        ResultActions resultActions = mvc.perform(
                post("/api/v1/members/refresh")
                        .cookie(cookie)
        ).andDo(print());

        // then - 새 accessToken은 응답 바디가 아니라 쿠키로만 내려간다
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("AccessToken 재발급 성공"))
                .andExpect(cookie().exists("accessToken"))
                .andExpect(cookie().httpOnly("accessToken", true));
    }
    @Test
    @DisplayName("RefreshToken 없으면 401")
    void t14() throws Exception {

        ResultActions resultActions = mvc.perform(
                post("/api/v1/members/refresh")
        ).andDo(print());

        resultActions
                .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("유효하지 않은 RefreshToken")
    void t15() throws Exception {

        Cookie cookie = new Cookie(
                "refreshToken",
                UUID.randomUUID().toString()
        );

        ResultActions resultActions = mvc.perform(
                post("/api/v1/members/refresh")
                        .cookie(cookie)
        ).andDo(print());

        resultActions
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 정보 조회 시 role도 함께 내려온다 - OAuth2 콜백이 토큰 없이 role을 판단하기 위함")
    void t16() throws Exception {
        preVerifyEmail("test@test.com");

        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발",
                                 "agreedToTerms": true
                            }
                            """)
        );

        // Given - 로그인 선행 (토큰은 응답 바디가 아니라 쿠키로 내려온다)
        Cookie accessTokenCookie = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "test@test.com",
                                         "password": "1234"
                                    }
                                    """)
                )
                .andReturn()
                .getResponse()
                .getCookie("accessToken");

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/members/me")
                                .cookie(accessTokenCookie)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }
    
    @Test
    @DisplayName("개인정보 동의 안 하면 회원가입 실패")
    void t17() throws Exception {
        preVerifyEmail("test@test.com");

        // When - agreedToTerms: false
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "test@test.com",
                                         "password": "1234",
                                         "industry": "IT/개발",
                                         "agreedToTerms": false
                                    }
                                    """)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("agreedToTerms 필드 누락 시 회원가입 실패")
    void t18() throws Exception {
        preVerifyEmail("test@test.com");

        // When - agreedToTerms 필드 자체를 안 보냄
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "test@test.com",
                                         "password": "1234",
                                         "industry": "IT/개발"
                                    }
                                    """)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인으로 발급받은 refreshToken 쿠키로 AccessToken을 재발급받을 수 있다")
    void t19() throws Exception {
        preVerifyEmail("test@test.com");
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                     "email": "test@test.com",
                                     "password": "1234",
                                     "industry": "IT/개발",
                                     "agreedToTerms": true
                                }
                                """)
        );

        // Given - 로그인해서 refreshToken 쿠키 발급
        ResultActions loginResult = mvc.perform(
                post("/api/v1/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                     "email": "test@test.com",
                                     "password": "1234"
                                }
                                """)
        );
        Cookie refreshTokenCookie = loginResult.andReturn().getResponse().getCookie("refreshToken");
        assertThat(refreshTokenCookie).isNotNull();

        // When - 그 refreshToken 쿠키로 재발급 요청
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/refresh")
                                .cookie(refreshTokenCookie)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    @DisplayName("같은 이메일로 로그인을 5번 연속 실패하면, 비밀번호가 맞아도 6번째부터는 429로 차단된다")
    void t21() throws Exception {
        preVerifyEmail("brute-force-test@test.com");
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                     "email": "brute-force-test@test.com",
                                     "password": "1234",
                                     "industry": "IT/개발",
                                     "agreedToTerms": true
                                }
                                """)
        );

        // When - 틀린 비밀번호로 5번 연속 실패
        for (int i = 0; i < 5; i++) {
            mvc.perform(
                    post("/api/v1/members/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                         "email": "brute-force-test@test.com",
                                         "password": "wrong-password"
                                    }
                                    """)
            ).andExpect(status().isUnauthorized());
        }

        // Then - 6번째는 비밀번호가 맞아도 429로 차단된다
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                             "email": "brute-force-test@test.com",
                                             "password": "1234"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.resultCode").value("429-1"));
    }

    @Test
    @DisplayName("로그인에 성공하면 실패 카운트가 초기화된다")
    void t22() throws Exception {
        preVerifyEmail("reset-test@test.com");
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                     "email": "reset-test@test.com",
                                     "password": "1234",
                                     "industry": "IT/개발",
                                     "agreedToTerms": true
                                }
                                """)
        );

        // Given - 틀린 비밀번호로 2번 실패 후 정상 로그인 성공
        for (int i = 0; i < 2; i++) {
            mvc.perform(
                    post("/api/v1/members/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                         "email": "reset-test@test.com",
                                         "password": "wrong-password"
                                    }
                                    """)
            ).andExpect(status().isUnauthorized());
        }
        mvc.perform(
                post("/api/v1/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                     "email": "reset-test@test.com",
                                     "password": "1234"
                                }
                                """)
        ).andExpect(status().isOk());

        // When - 다시 틀린 비밀번호로 로그인 (실패 카운트가 리셋됐다면 429가 아니라 401이어야 함)
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                             "email": "reset-test@test.com",
                                             "password": "wrong-password"
                                        }
                                        """)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isUnauthorized());
    }
}
