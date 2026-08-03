package com.back.domain.chat.chatRoom.controller;

import com.back.domain.bot.BotAccounts;
import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.match.matchRequest.entity.MatchRequest;
import com.back.domain.match.matchRequest.entity.Situation;
import com.back.domain.match.matchRequest.repository.MatchRequestRepository;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ApiV1ChatRoomControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Test
    @DisplayName("채팅방 정보 조회 성공")
    void t1() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("testuser1@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, member, "익명의 동료"));

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/rooms/" + roomId)
                                .cookie(new Cookie("accessToken", accessToken)) // 쿠키 주입
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("채팅방 정보 조회 성공"))
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.maxParticipants").value(2))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    @DisplayName("존재하지 않는 채팅방 정보 조회 시 실패")
    void t2() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("testuser2@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);
        UUID nonexistentRoomId = UUID.randomUUID();

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/rooms/" + nonexistentRoomId)
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isNotFound()) // 404 Not Found
                .andExpect(jsonPath("$.resultCode").value("404-1"))
                .andExpect(jsonPath("$.msg").value("채팅방을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("참여하지 않은 채팅방 정보 조회 시 실패")
    void t3() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("testuser3@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/rooms/" + roomId)
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isForbidden()) // 403 Forbidden
                .andExpect(jsonPath("$.resultCode").value("403-1"))
                .andExpect(jsonPath("$.msg").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("채팅방 종료 성공")
    void t4() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("closeuser1@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, member, "익명의 동료"));

        // When
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/rooms/" + roomId)
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("채팅방 상태 수정 성공 (채팅방 종료)"))
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.maxParticipants").value(2))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.closedAt").exists());
    }

    @Test
    @DisplayName("참여하지 않은 채팅방 종료 시 실패")
    void t5() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("closeuser2@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        // When
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/rooms/" + roomId)
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isForbidden()) // 403 Forbidden
                .andExpect(jsonPath("$.resultCode").value("403-1"))
                .andExpect(jsonPath("$.msg").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("이미 종료된 채팅방 종료 시 실패")
    void t6() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("closeuser3@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.CLOSED, 2));
        UUID roomId = chatRoom.getId();

        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, member, "익명의 동료"));

        // When
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/rooms/" + roomId)
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isConflict()) // 409 Conflict
                .andExpect(jsonPath("$.resultCode").value("409-1"))
                .andExpect(jsonPath("$.msg").value("이미 종료된 채팅방입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 채팅방 종료 시 실패")
    void t7() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("closeuser4@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);
        UUID nonexistentRoomId = UUID.randomUUID();

        // When
        ResultActions resultActions = mvc
                .perform(
                        patch("/api/v1/rooms/" + nonexistentRoomId)
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isNotFound()) // 404 Not Found
                .andExpect(jsonPath("$.resultCode").value("404-1"))
                .andExpect(jsonPath("$.msg").value("채팅방을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("현재 활성화된 채팅방 조회 성공")
    void t8() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("activeuser1@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, member, "익명의 동료"));

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/rooms/active")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("현재 활성화된 채팅방 조회 성공"))
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.maxParticipants").value(2))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.closedAt").doesNotExist());
    }

    @Test
    @DisplayName("진행 중인 채팅방이 존재하지 않을 때 조회 성공")
    void t9() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("activeuser2@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/rooms/active")
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-2"))
                .andExpect(jsonPath("$.msg").value("진행 중인 채팅방이 존재하지 않습니다."))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    @DisplayName("로그인하지 않고 활성 채팅방 조회 시 실패")
    void t10() throws Exception {
        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/rooms/active")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"))
                .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."));
    }

    @Test
    @DisplayName("채팅방 조회 시 상대방이 선택한 상황이 노출된다")
    void t11() throws Exception {
        // Given
        Member member = memberService.joinWithoutEmailVerification("oppuser1@test.com", "1234", IT, "USER");
        Member opponent = memberService.joinWithoutEmailVerification("oppuser2@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, member, "익명의 동료"));
        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, opponent, "익명의 동료"));

        MatchRequest myRequest = new MatchRequest(member, Situation.NIGHT_WORK);
        myRequest.matchWith(chatRoom);
        matchRequestRepository.save(myRequest);

        MatchRequest opponentRequest = new MatchRequest(opponent, Situation.MEETING_BOMB);
        opponentRequest.matchWith(chatRoom);
        matchRequestRepository.save(opponentRequest);

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/rooms/" + roomId)
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then - 내가 고른 상황이 아니라 상대방(opponent)이 고른 상황이 내려와야 한다
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.opponentSituation").value("회의 폭탄"));
    }

    @Test
    @DisplayName("봇과 매칭된 경우에도 상대방이 선택한 상황이 노출된다")
    void t12() throws Exception {
        // Given - matchWithBot()이 실제로 하듯, 봇의 MatchRequest도 요청자와 같은 situation으로 생성
        Member member = memberService.joinWithoutEmailVerification("oppuser3@test.com", "1234", IT, "USER");
        Member bot = memberService.findByEmail(BotAccounts.emailFor(IT))
                .orElseThrow();
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, member, "익명의 동료"));
        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, bot, "익명의 동료"));

        MatchRequest myRequest = new MatchRequest(member, Situation.BOSS_BLAME);
        myRequest.matchWith(chatRoom);
        matchRequestRepository.save(myRequest);

        MatchRequest botRequest = new MatchRequest(bot, Situation.BOSS_BLAME);
        botRequest.matchWith(chatRoom);
        matchRequestRepository.save(botRequest);

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/rooms/" + roomId)
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.opponentSituation").value("상사 억까"));
    }

    @Test
    @DisplayName("상대방 매칭 요청 정보가 없으면 opponentSituation 없이 응답한다")
    void t13() throws Exception {
        // Given - MatchRequest 없이 채팅방/참여자만 존재하는 상황(t1과 동일한 셋업)
        Member member = memberService.joinWithoutEmailVerification("oppuser4@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getId();

        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, member, "익명의 동료"));

        // When
        ResultActions resultActions = mvc
                .perform(
                        get("/api/v1/rooms/" + roomId)
                                .cookie(new Cookie("accessToken", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andDo(print());

        // Then - @JsonInclude(NON_NULL)이라 값이 없으면 필드 자체가 응답에서 빠진다
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.opponentSituation").doesNotExist());
    }
}