package com.back.domain.report.report.controller;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomMessage.entity.ChatMessage;
import com.back.domain.chat.chatRoomMessage.repository.ChatMessageRepository;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.entity.Industry;
import com.back.domain.member.member.service.MemberService;
import com.back.domain.report.report.entity.Report;
import com.back.domain.report.report.entity.ReportedMessage;
import com.back.domain.report.report.repository.ReportRepository;
import com.back.domain.report.report.repository.ReportedMessageRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.back.domain.member.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ApiV1ReportControllerTest {
    private final List<Member> createdMembers = new ArrayList<>();

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

    @AfterEach
    void cleanUp() {
        if (TestTransaction.isActive()) {
            TestTransaction.end();
        }
        TestTransaction.start();
        reportedMessageRepository.deleteAll();
        reportRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatRoomParticipantRepository.deleteAll();
        chatRoomRepository.deleteAll();
        createdMembers.forEach(memberRepository::delete);
        createdMembers.clear();
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @Test
    @DisplayName("신고 접수 성공")
    void t1() throws Exception {
        // Given
        // 1. 신고자와 피신고자 회원가입 및 토큰 발급
        Member reporter = memberService.joinWithoutEmailVerification("reporter@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(reporter);
        createdMembers.add(reported);
        String accessToken = memberService.genAccessToken(reporter);

        // 2. 대화방 생성 및 참여자 등록
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        ChatRoomParticipant p1 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reporter, "익명의 동료"));
        ChatRoomParticipant p2 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reported, "익명의 동료"));

        // 3. 원본 욕설 메시지 저장
        ChatMessage targetMessage = chatMessageRepository.save(new ChatMessage(chatRoom, p2, "너 일 그따구로 할 거면 사표 써라"));
        UUID reportedMessageId = targetMessage.getId();

        // Given 단계 데이터 영속성 적재를 위한 강제 커밋
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // API 격리 실행을 위한 새로운 트랜잭션 수동 개시
        TestTransaction.start();

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "사내 비방 및 욕설"
                                        }
                                        """.formatted(roomId, reportedMessageId))
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.msg").value("신고 생성 성공"));

        // API가 마친 메인 트랜잭션 강제 커밋 -> AFTER_COMMIT 이벤트 유발
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // Awaitility를 사용해 비동기 저장이 완료되는 순간 즉시 대기 탈출
        await()
                .atMost(2, SECONDS)
                .untilAsserted(() ->
                        assertThat(reportedMessageRepository.count()).isEqualTo(1)
                );
    }

    @Test
    @DisplayName("존재하지 않는 채팅방 ID로 신고 시 실패")
    void t2() throws Exception {
        // Given
        Member reporter = memberService.joinWithoutEmailVerification("reporter2@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(reporter);
        String accessToken = memberService.genAccessToken(reporter);
        UUID nonExistentRoomId = UUID.randomUUID();
        UUID mockMessageId = UUID.randomUUID();

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "사내 비방 및 욕설"
                                        }
                                        """.formatted(nonExistentRoomId, mockMessageId))
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
                .andExpect(jsonPath("$.msg").value("채팅방을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 메시지 ID로 신고 시 실패")
    void t3() throws Exception {
        // Given
        Member reporter = memberService.joinWithoutEmailVerification("reporter3@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(reporter);
        String accessToken = memberService.genAccessToken(reporter);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        UUID nonExistentMessageId = UUID.randomUUID();

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "사내 비방 및 욕설"
                                        }
                                        """.formatted(roomId, nonExistentMessageId))
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-2"))
                .andExpect(jsonPath("$.msg").value("신고 대상 메시지를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("비로그인 사용자가 신고 시도 시 실패")
    void t4() throws Exception {
        // Given
        UUID mockRoomId = UUID.randomUUID();
        UUID mockMessageId = UUID.randomUUID();

        // When (액세스 토큰 쿠키 없이 호출)
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "사내 비방 및 욕설"
                                        }
                                        """.formatted(mockRoomId, mockMessageId))
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"))
                .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."));
    }

    @Test
    @DisplayName("자신이 작성한 메시지 신고 시도 시 실패")
    void t5() throws Exception {
        // Given
        Member reporter = memberService.joinWithoutEmailVerification("reporter5@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(reporter);
        String accessToken = memberService.genAccessToken(reporter);
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();
        ChatRoomParticipant p1 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reporter, "익명의 동료"));
        // 본인이 작성한 메시지
        ChatMessage ownMessage = chatMessageRepository.save(new ChatMessage(chatRoom, p1, "내가 쓴 부적절한 글"));
        UUID reportedMessageId = ownMessage.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "장난 신고"
                                        }
                                        """.formatted(roomId, reportedMessageId))
                )
                .andDo(print());
        // Then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.msg").value("자신을 신고할 수 없습니다."));
    }
    @Test
    @DisplayName("동일한 메시지 중복 신고 시도 시 실패")
    void t6() throws Exception {
        // Given
        Member reporter = memberService.joinWithoutEmailVerification("reporter6@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported6@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(reporter);
        createdMembers.add(reported);
        String accessToken = memberService.genAccessToken(reporter);
        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();
        ChatRoomParticipant p1 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reporter, "익명1"));
        ChatRoomParticipant p2 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reported, "익명2"));
        ChatMessage targetMessage = chatMessageRepository.save(new ChatMessage(chatRoom, p2, "중복 신고해봐라"));
        UUID reportedMessageId = targetMessage.getId();
        // 1차 신고 접수 (DB에 적재하기 위해 Given 단계에서 직접 등록 처리)
        reportRepository.save(new Report(reporter, reported, chatRoom, reportedMessageId, "1차 신고"));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        // When (2차 중복 신고 요청)
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "2차 중복 신고"
                                        }
                                        """.formatted(roomId, reportedMessageId))
                )
                .andDo(print());
        // Then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-2"))
                .andExpect(jsonPath("$.msg").value("이미 신고된 메시지입니다."));
    }

    @Test
    @DisplayName("신고 유발 메시지 기준 이전 대화 최대 30개만 제한 백업 검증")
    void t7() throws Exception {
        // Given
        Member reporter = memberService.joinWithoutEmailVerification("reporter7@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported7@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(reporter);
        createdMembers.add(reported);
        String accessToken = memberService.genAccessToken(reporter);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        ChatRoomParticipant p1 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reporter, "익명1"));
        ChatRoomParticipant p2 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reported, "익명2"));

        // 💡 1부터 35까지 총 35개의 메시지를 순서대로 생성하여 DB 적재 (시차 분리를 위해 10ms 딜레이 부여)
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            messages.add(chatMessageRepository.save(
                    new ChatMessage(chatRoom, p2, "부적절한 메시지 내용 " + i)
            ));
            Thread.sleep(10);
        }

        // 32번째 메시지(인덱스 31)를 신고 대상 메시지로 선정
        ChatMessage targetMessage = messages.get(31);
        UUID reportedMessageId = targetMessage.getId();

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "30개 한계선 테스트"
                                        }
                                        """.formatted(roomId, reportedMessageId))
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.msg").value("신고 생성 성공"));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        // [검증] Awaitility를 사용하여 최대 30개 제한 정책이 작동하는지 체크
        await()
                .atMost(2, SECONDS)
                .untilAsserted(() -> {
                    // 1. 신고 메시지 이전의 32개 대화 중 최대 30개만 복사되어 백업되었는지 검증
                    long count = reportedMessageRepository.count();
                    assertThat(count).isEqualTo(30);

                    // 2. 신고된 32번째 메시지(Target)가 정상적으로 수집 목록에 포함되어 isTarget=true로 저장되었는지 검증
                    List<ReportedMessage> savedList = reportedMessageRepository.findAll();
                    boolean hasTarget = savedList.stream()
                            .anyMatch(msg -> msg.isTarget() && msg.getContent().contains("부적절한 메시지 내용 32"));
                    assertThat(hasTarget).isTrue();

                    // 3. 신고 대상 메시지보다 나중에 작성된 33~35번째 메시지는 백업 대상에서 배제되었는지 검증
                    boolean hasLaterMessage = savedList.stream()
                            .anyMatch(msg -> msg.getContent().contains("부적절한 메시지 내용 33")
                                    || msg.getContent().contains("부적절한 메시지 내용 35"));
                    assertThat(hasLaterMessage).isFalse();
                });
    }

    @Test
    @DisplayName("채팅방 참여자가 아닌 유저가 신고 시도 시 실패")
    void t8() throws Exception {
        // Given
        Member reporter = memberService.joinWithoutEmailVerification("reporter8@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported8@test.com", "1234", Industry.IT, "USER");
        Member outsider = memberService.joinWithoutEmailVerification("outsider8@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(reporter);
        createdMembers.add(reported);
        createdMembers.add(outsider);
        String outsiderToken = memberService.genAccessToken(outsider);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        ChatRoomParticipant p1 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reporter, "익명1"));
        ChatRoomParticipant p2 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, reported, "익명2"));

        ChatMessage targetMessage = chatMessageRepository.save(new ChatMessage(chatRoom, p2, "부적절한 발언"));
        UUID reportedMessageId = targetMessage.getId();

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .cookie(new Cookie("accessToken", outsiderToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "참여자 아님 신고 시도"
                                        }
                                        """.formatted(roomId, reportedMessageId))
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.resultCode").value("403-2"))
                .andExpect(jsonPath("$.msg").value("채팅방 참여자만 신고할 수 있습니다."));
    }

    @Test
    @DisplayName("신고 메시지가 요청한 채팅방에 속하지 않을 경우 실패")
    void t9() throws Exception {
        // Given
        Member reporter = memberService.joinWithoutEmailVerification("reporter9@test.com", "1234", Industry.IT, "USER");
        Member reported = memberService.joinWithoutEmailVerification("reported9@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(reporter);
        createdMembers.add(reported);
        String accessToken = memberService.genAccessToken(reporter);

        ChatRoom chatRoom1 = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        ChatRoom chatRoom2 = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));

        ChatRoomParticipant p1 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom1, reporter, "익명1"));
        ChatRoomParticipant p2 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom1, reported, "익명2"));

        ChatRoomParticipant p3 = chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom2, reported, "익명3"));
        ChatMessage targetMessage = chatMessageRepository.save(new ChatMessage(chatRoom2, p3, "엉뚱한 방의 대화"));
        UUID reportedMessageId = targetMessage.getId();

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "방 불일치 메시지 신고"
                                        }
                                        """.formatted(chatRoom1.getId(), reportedMessageId))
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-3"))
                .andExpect(jsonPath("$.msg").value("요청한 채팅방의 메시지가 아닙니다."));
    }

    @Test
    @DisplayName("신고 사유가 공백이거나 빈 문자열일 경우 유효성 검증 실패")
    void t10() throws Exception {
        // Given
        Member reporter = memberService.joinWithoutEmailVerification("reporter10@test.com", "1234", Industry.IT, "USER");
        createdMembers.add(reporter);
        String accessToken = memberService.genAccessToken(reporter);
        UUID roomId = UUID.randomUUID();
        UUID reportedMessageId = UUID.randomUUID();

        // When
        ResultActions resultActions = mvc
                .perform(
                        post("/api/v1/reports")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "roomId": "%s",
                                            "reportedMessageId": "%s",
                                            "reason": "   "
                                        }
                                        """.formatted(roomId, reportedMessageId))
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isBadRequest());
    }
}

