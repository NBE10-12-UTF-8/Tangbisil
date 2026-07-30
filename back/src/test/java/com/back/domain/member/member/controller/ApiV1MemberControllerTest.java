package com.back.domain.member.member.controller;

import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;
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


import java.util.UUID;

import static com.back.domain.member.member.entity.Industry.*;
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

    @Test
    @DisplayName("회원가입")
    void t1() throws Exception {
        // When
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
        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                     "email": "test@test.com",
                                     "password": "1234",
                                     "industry": "IT/개발"
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

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("로그인 생성 성공"))
                .andExpect(jsonPath("$.data.grantType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").exists());
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
        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발"
                            }
                            """)
        );

        // Given - 로그인 선행
        String loginResponse = mvc.perform(
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
                .getContentAsString();

        String accessToken = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/logout")
                                .header("Authorization", "Bearer " + accessToken)
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
        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발"
                            }
                            """)
        );

        // Given - 로그인 선행
        String loginResponse = mvc.perform(
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
                .getContentAsString();

        String accessToken = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/members/me")
                                .header("Authorization", "Bearer " + accessToken)
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
        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발"
                            }
                            """)
        );

        // Given - 로그인 선행
        String loginResponse = mvc.perform(
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
                .getContentAsString();

        String accessToken = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();

        // When
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/members/me")
                                .header("Authorization", "Bearer " + accessToken)
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
        Member member1 = memberService.join("history1@test.com", "1234", IT, "USER");
        Member member2 = memberService.join("history2@test.com", "1234", IT, "USER");
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

        String statusResponse = mvc.perform(
                get("/api/v1/matches/" + matchRequestId)
                        .header("Authorization", "Bearer " + accessToken2)
        ).andReturn().getResponse().getContentAsString();

        String roomId = new ObjectMapper().readTree(statusResponse).path("data").path("chatRoomId").asText();

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
        Member member = memberService.join("history3@test.com", "1234", IT, "USER");
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
        Member member1 = memberService.join("history4@test.com", "1234", IT, "USER");
        Member member2 = memberService.join("history5@test.com", "1234", IT, "USER");
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
        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발"
                            }
                            """)
        );

        // Given - 로그인 선행
        String loginResponse = mvc.perform(
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
                .getContentAsString();

        String accessToken = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();

        // When
        ResultActions resultActions = mvc
                .perform(
                        delete("/api/v1/members/me")
                                .header("Authorization", "Bearer " + accessToken)
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
        Member member = memberService.join(
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

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("AccessToken 재발급 성공"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken")
                        .value(refreshToken.toString()));
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
        // Given - 회원가입 선행
        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                 "email": "test@test.com",
                                 "password": "1234",
                                 "industry": "IT/개발"
                            }
                            """)
        );

        // Given - 로그인 선행
        String loginResponse = mvc.perform(
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
                .getContentAsString();

        String accessToken = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/members/me")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }
}
