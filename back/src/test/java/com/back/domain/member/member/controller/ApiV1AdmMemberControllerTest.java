package com.back.domain.member.member.controller;

import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.domain.member.member.repository.MemberRepository;

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
public class ApiV1AdmMemberControllerTest {
    @Autowired
    private MemberService memberService;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("관리자 회원 다건 조회")
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
                        get("/api/v1/admin/members")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "0")
                                .param("size", "10")
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("회원 다건 조회 성공"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.pageable.pageNumber").value(0))
                .andExpect(jsonPath("$.data.pageable.pageSize").value(10));
    }
    @Test
    @DisplayName("관리자 회원 단건 조회")
    void t2() throws Exception {
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

        // Given - 조회할 memberId 가져오기
        String membersResponse = mvc.perform(
                        get("/api/v1/admin/members")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String memberId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(membersResponse)
                .path("data")
                .path("content")
                .get(0)
                .path("memberId")
                .asText();

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/members/" + memberId)
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("회원 단건 조회 성공"))
                .andExpect(jsonPath("$.data.memberId").exists())
                .andExpect(jsonPath("$.data.email").exists())
//                .andExpect(jsonPath("$.data.industry").exists())
                .andExpect(jsonPath("$.data.isSuspended").exists())
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    @DisplayName("관리자 회원 단건 조회 - 이메일로 조회")
    void t2_1() throws Exception {
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

        Member target = memberService.joinWithoutEmailVerification("lookup_by_email@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");

        // When - UUID 대신 이메일로 조회
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/members/" + target.getEmail())
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.memberId").value(target.getUuid().toString()))
                .andExpect(jsonPath("$.data.email").value("lookup_by_email@test.com"));
    }

    @Test
    @DisplayName("관리자 권한으로 일반 회원 정지 토글 성공")
    void t3() throws Exception {
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

        // Given - 정지시킬 일반 회원 가입
        Member user = memberService.joinWithoutEmailVerification("user_suspend_adm@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");

        // When
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/admin/members/" + user.getUuid() + "/suspend")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("계정 정지 상태 토글 성공"))
                .andExpect(jsonPath("$.data.memberId").value(user.getUuid().toString()))
                .andExpect(jsonPath("$.data.isSuspended").value(true));
    }

    @Test
    @DisplayName("일반 회원 권한으로 어드민 정지 API 접근 시 403 Forbidden 차단")
    void t4() throws Exception {
        // Given - 일반 회원 2명 가입 및 1명 로그인
        Member user1 = memberService.joinWithoutEmailVerification("user1_susp_adm@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");
        Member user2 = memberService.joinWithoutEmailVerification("user2_susp_adm@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");

        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "user1_susp_adm@test.com",
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
                        patch("/api/v1/admin/members/" + user2.getUuid() + "/suspend")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then
        resultActions.andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자가 자기 자신을 제재하려고 시도할 시 400 Bad Request 실패")
    void t5() throws Exception {
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

        Member admin = memberService.findByEmail("admin@test.com").orElseThrow();

        // When
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/admin/members/" + admin.getUuid() + "/suspend")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.msg").value("자기 자신은 제재할 수 없습니다."));
    }

    @Test
    @DisplayName("정지된 회원도 /me 조회는 가능하다")
    void t6() throws Exception {
        // Given - 일반 회원 가입 및 로그인 후, 정지 상태로 변경
        Member user = memberService.joinWithoutEmailVerification("user_blocked_adm@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");

        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "user_blocked_adm@test.com",
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

        // 강제로 회원을 정지 상태로 DB 수정 (토글 활용)
        user.toggleSuspended();
        memberRepository.saveAndFlush(user);

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/members/me")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then - 정지 상태여도 내 정보 조회는 허용된다
        resultActions.andExpect(status().isOk());
    }

    @Test
    @DisplayName("정지된 회원도 로그아웃은 가능하다")
    void t7() throws Exception {
        Member user = memberService.joinWithoutEmailVerification("user_susp_logout@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");

        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "user_susp_logout@test.com",
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

        user.toggleSuspended();
        memberRepository.saveAndFlush(user);

        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/members/logout")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        resultActions.andExpect(status().isOk());
    }

    @Test
    @DisplayName("정지된 회원은 산업군 수정이 차단된다")
    void t8() throws Exception {
        Member user = memberService.joinWithoutEmailVerification("user_susp_patch@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");

        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "user_susp_patch@test.com",
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

        user.toggleSuspended();
        memberRepository.saveAndFlush(user);

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

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"));
    }

    @Test
    @DisplayName("정지된 회원은 탈퇴가 차단된다")
    void t9() throws Exception {
        Member user = memberService.joinWithoutEmailVerification("user_susp_delete@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");

        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "user_susp_delete@test.com",
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

        user.toggleSuspended();
        memberRepository.saveAndFlush(user);

        ResultActions resultActions = mvc
                .perform(
                        delete("/api/v1/members/me")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"));
    }

    @Test
    @DisplayName("정지된 회원은 내 정보 조회/로그아웃 외 API는 여전히 403 차단된다 (매칭 이력 예시)")
    void t10() throws Exception {
        Member user = memberService.joinWithoutEmailVerification("user_susp_blocked_other@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");

        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "user_susp_blocked_other@test.com",
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

        user.toggleSuspended();
        memberRepository.saveAndFlush(user);

        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/members/me/matches")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-1"));
    }

    @Test
    @DisplayName("관리자용 회원 목록 isSuspended=true 필터링 조회 성공")
    void t11() throws Exception {
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

        // Given - 정지 상태인 검증용 회원 생성
        Member suspended = memberService.joinWithoutEmailVerification("suspended_t11@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");
        suspended.toggleSuspended();
        memberRepository.saveAndFlush(suspended);

        // When - isSuspended=true 필터 얹어서 목록 조회 요청
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/members")
                                .param("isSuspended", "true")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then - 오직 정지된 회원만 내려오는지 검증 (공허한 참 방지를 위해 isNotEmpty도 같이 검증)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("회원 다건 조회 성공"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.content[*].isSuspended", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    @DisplayName("관리자용 회원 목록 isSuspended=false 필터링 조회 성공")
    void t12() throws Exception {
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

        // Given - 정상(비정지) 상태인 검증용 회원 생성
        memberService.joinWithoutEmailVerification("active_t12@test.com", "1234", com.back.domain.member.member.entity.Industry.IT, "USER");

        // When - isSuspended=false 필터 얹어서 목록 조회 요청
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/members")
                                .param("isSuspended", "false")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then - 오직 비정지 회원만 내려오는지 검증 (공허한 참 방지를 위해 isNotEmpty도 같이 검증)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("회원 다건 조회 성공"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.content[*].isSuspended", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
    }
}