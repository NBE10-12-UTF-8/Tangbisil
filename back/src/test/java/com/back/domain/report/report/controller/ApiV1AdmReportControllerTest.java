package com.back.domain.report.report.controller;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import com.back.domain.chat.chatRoomMessage.repository.ChatMessageRepository;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.member.service.MemberService;
import com.back.domain.report.report.entity.Report;
import com.back.domain.report.report.entity.ReportedMessage;
import com.back.domain.report.report.repository.ReportRepository;
import com.back.domain.report.report.repository.ReportedMessageRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ApiV1AdmReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportedMessageRepository reportedMessageRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("관리자용 신고 목록 페이징 조회 성공")
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

        // Given - 1건의 신고 데이터 임의 적재
        Member reporter = memberService.joinWithoutEmailVerification("reporter_list@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported_list@test.com", "1234", Industry.IT, "USER");

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        Report report = reportRepository.save(new Report(reporter, reported, chatRoom, UUID.randomUUID(), "욕설 및 부적절 대화"));

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/reports")
                                .header("Authorization", "Bearer " + accessToken)
                                .param("page", "0")
                                .param("size", "10")
                )
                .andDo(print());

        // Then (팀의 공통 페이징 규격 검증)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("신고 목록 조회 성공"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].reportId").value(report.getId().toString()))
                .andExpect(jsonPath("$.data.pageable.pageNumber").value(0))
                .andExpect(jsonPath("$.data.pageable.pageSize").value(10))
                .andExpect(jsonPath("$.data.totalElements").exists())
                .andExpect(jsonPath("$.data.totalPages").exists());
    }

    @Test
    @DisplayName("일반 회원 권한으로 관리자용 신고 API 접근 시 403 Forbidden 차단")
    void t2() throws Exception {
        // Given - 일반 회원 가입 및 로그인
        Member user = memberService.joinWithoutEmailVerification("normal_user@test.com", "1234", Industry.IT, "USER");

        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "normal_user@test.com",
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

        // When - 어드민 신고 API 목록 조회 요청
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/reports")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then - 403 Forbidden 차단 검증
        resultActions.andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("특정 신고의 증거 상세 대화 조회 및 가독성 라벨링 치환 검증 성공")
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

        // Given - 신고자, 피신고자, 참여자 계정 생성
        Member reporter = memberService.joinWithoutEmailVerification("reporter_detail@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported_detail@test.com", "1234", Industry.IT, "USER");
        Member participant = memberService.joinWithoutEmailVerification("participant_detail@test.com", "1234", Industry.IT, "USER");

        // 대화방 생성 및 참여자 등록
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 3));
        ChatRoomParticipant pReporter = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reporter, "익명의 동료"));
        ChatRoomParticipant pReported = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reported, "익명의 동료"));
        ChatRoomParticipant pParticipant = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, participant, "익명의 동료"));

        // 시간순 메시지 적재 (1. 참여자 -> 2. 신고자 -> 3. 피신고자 최종 욕설 타겟)
        ChatMessage msg1 = chatMessageRepository.save(new ChatMessage(chatRoom, pParticipant, "어제 회의 때 말씀하셨던 그 기획서 어디 갔나요?"));
        ChatMessage msg2 = chatMessageRepository.save(new ChatMessage(chatRoom, pReporter, "메일로 이미 공유드렸으니 확인 부탁드립니다."));
        ChatMessage msg3 = chatMessageRepository.save(new ChatMessage(chatRoom, pReported, "너 일 그따구로 할 거면 사표 써라"));

        // 신고 생성 및 백업 증거 메시지 적재 (isTarget도 설정)
        Report report = reportRepository.save(new Report(reporter, reported, chatRoom, msg3.getId(), "사내 정치성 허위 비방 및 욕설"));
        
        reportedMessageRepository.save(new ReportedMessage(report, pParticipant.getMember().getId(), pParticipant.getNickname(), msg1.getContent(), msg1.getCreatedAt(), false));
        reportedMessageRepository.save(new ReportedMessage(report, pReporter.getMember().getId(), pReporter.getNickname(), msg2.getContent(), msg2.getCreatedAt(), false));
        reportedMessageRepository.save(new ReportedMessage(report, pReported.getMember().getId(), pReported.getNickname(), msg3.getContent(), msg3.getCreatedAt(), true));

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/reports/" + report.getId())
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then (라벨링 정합성 및 명세서 JSON 규격 검증)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("신고 상세 조회 성공"))
                .andExpect(jsonPath("$.data.reportId").value(report.getId().toString()))
                .andExpect(jsonPath("$.data.reportedMessages").isArray())
                // 1번째 메시지 검증 (참여자 A)
                .andExpect(jsonPath("$.data.reportedMessages[0].content").value("어제 회의 때 말씀하셨던 그 기획서 어디 갔나요?"))
                .andExpect(jsonPath("$.data.reportedMessages[0].senderLabel").value("참여자 A"))
                .andExpect(jsonPath("$.data.reportedMessages[0].isTarget").value(false))
                // 2번째 메시지 검증 (신고자)
                .andExpect(jsonPath("$.data.reportedMessages[1].content").value("메일로 이미 공유드렸으니 확인 부탁드립니다."))
                .andExpect(jsonPath("$.data.reportedMessages[1].senderLabel").value("신고자"))
                .andExpect(jsonPath("$.data.reportedMessages[1].isTarget").value(false))
                // 3번째 메시지 검증 (피신고자 - 최종 타겟)
                .andExpect(jsonPath("$.data.reportedMessages[2].content").value("너 일 그따구로 할 거면 사표 써라"))
                .andExpect(jsonPath("$.data.reportedMessages[2].senderLabel").value("피신고자"))
                .andExpect(jsonPath("$.data.reportedMessages[2].isTarget").value(true));
    }

    @Test
    @DisplayName("관리자용 신고서 처리 상태 수정 토글 성공 (PENDING -> PROCESSED)")
    void t4() throws Exception {
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

        // Given - 임시 신고 데이터 생성 (기본 PENDING)
        Member reporter = memberService.joinWithoutEmailVerification("reporter_toggle@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported_toggle@test.com", "1234", Industry.IT, "USER");
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        Report report = reportRepository.save(new Report(reporter, reported, chatRoom, UUID.randomUUID(), "욕설"));

        // When - PATCH 처리 상태 토글 요청
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/admin/reports/" + report.getId() + "/status")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then - 토글 결과 검증 (PENDING -> PROCESSED)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("신고서 처리 상태 수정 성공"))
                .andExpect(jsonPath("$.data.reportId").value(report.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("PROCESSED"));
    }

    @Test
    @DisplayName("일반 회원 권한으로 관리자용 신고 처리 상태 수정 API 접근 시 403 Forbidden 차단")
    void t5() throws Exception {
        // Given - 일반 회원 가입 및 로그인
        Member user = memberService.joinWithoutEmailVerification("normal_toggle_user@test.com", "1234", Industry.IT, "USER");

        String loginResponse = mvc.perform(
                        post("/api/v1/members/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                         "email": "normal_toggle_user@test.com",
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

        // Given - 임시 신고 데이터 생성
        Member reporter = memberService.joinWithoutEmailVerification("reporter_toggle_fail@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported_toggle_fail@test.com", "1234", Industry.IT, "USER");
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        Report report = reportRepository.save(new Report(reporter, reported, chatRoom, UUID.randomUUID(), "욕설"));

        // When - 일반 유저가 어드민 토글 API 호출
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/admin/reports/" + report.getId() + "/status")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then - 403 Forbidden 차단 검증
        resultActions.andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 신고 ID로 처리 상태 수정 요청 시 404-1 에러 반환")
    void t6() throws Exception {
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

        // Given - 임의의 존재하지 않는 신고 ID 생성
        UUID nonExistentId = UUID.randomUUID();

        // When - PATCH 처리 상태 토글 요청
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/admin/reports/" + nonExistentId + "/status")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then - 404 Not Found 및 404-1 에러 검증
        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
                .andExpect(jsonPath("$.msg").value("존재하지 않는 신고서입니다."));
    }

    @Test
    @DisplayName("관리자용 신고 목록 PENDING 필터링 조회 성공")
    void t7() throws Exception {
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

        // Given - PENDING 상태인 검증용 데이터 직접 생성
        Member reporter = memberService.joinWithoutEmailVerification("reporter_t7@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported_t7@test.com", "1234", Industry.IT, "USER");
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        reportRepository.save(new Report(reporter, reported, chatRoom, UUID.randomUUID(), "PENDING 검증용 사유"));

        // When - status=PENDING 필터 얹어서 목록 조회 요청
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/reports")
                                .param("status", "PENDING")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then - 오직 PENDING 상태인 신고만 내려오는지 검증 (공허한 참 방지를 위해 isNotEmpty도 같이 검증)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("신고 목록 조회 성공"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty())
                .andExpect(jsonPath("$.data.content[*].status", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PENDING"))));
    }

    @Test
    @DisplayName("관리자용 신고 목록 PROCESSED 필터링 조회 성공")
    void t8() throws Exception {
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

        // Given - PROCESSED 상태인 검증용 데이터 직접 생성
        Member reporter = memberService.joinWithoutEmailVerification("reporter_t8@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported_t8@test.com", "1234", Industry.IT, "USER");
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        Report report = new Report(reporter, reported, chatRoom, UUID.randomUUID(), "PROCESSED 검증용 사유");
        report.toggleStatus();
        reportRepository.save(report);

        // When - status=PROCESSED 필터 얹어서 목록 조회 요청
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/admin/reports")
                                .param("status", "PROCESSED")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andDo(print());

        // Then - 오직 PROCESSED 상태인 신고만 내려오는지 검증 (공허한 참 방지를 위해 isNotEmpty도 같이 검증)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("신고 목록 조회 성공"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty()) 
                .andExpect(jsonPath("$.data.content[*].status", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PROCESSED"))));
    }
}



