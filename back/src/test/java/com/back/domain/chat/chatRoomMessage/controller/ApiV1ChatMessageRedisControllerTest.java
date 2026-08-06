package com.back.domain.chat.chatRoomMessage.controller;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoom.service.ChatRoomService;
import com.back.domain.chat.chatRoomMessage.dto.RedisChatMessageDto;
import com.back.domain.chat.chatRoomMessage.repository.ChatMessageRepository;
import com.back.domain.chat.chatRoomMessage.service.ChatMessageService;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.emailVerification.entity.EmailVerificationToken;
import com.back.domain.member.emailVerification.repository.EmailVerificationTokenRepository;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.member.service.MemberService;
import com.back.support.TestAccessTokenFactory;
import com.back.standard.util.Ut;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction; // ⭐️ 테스트 내 트랜잭션 종료 유틸리티 임포트
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // ⭐️ 다른 테스트와의 데이터 격리 및 조화를 위해 다시 붙입니다.
public class ApiV1ChatMessageRedisControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @MockitoSpyBean
    private RedisTemplate<String, String> redisTemplate;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private Member testMember1;
    private Member testMember2;
    private ChatRoom testRoom;
    private String accessToken1;
    private String accessToken2;

    @BeforeEach
    void setUp() throws Exception {
        boolean isRedisAvailable = false;
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(redisHost, redisPort), 1000);
            isRedisAvailable = socket.isConnected();
        } catch (Exception ignored) {
        }
        Assumptions.assumeTrue(isRedisAvailable, "로컬 Redis 서버가 꺼져 있어 통합 컨트롤러 테스트를 스킵합니다.");

        reset(redisTemplate);

        testMember1 = signupAndLogin("redis_user1@test.com", "IT/개발");
        testMember2 = signupAndLogin("redis_user2@test.com", "IT/개발");

        List<Member> members = new ArrayList<>();
        members.add(testMember1);
        members.add(testMember2);
        testRoom = chatRoomService.createChatRoom(members);

        accessToken1 = getAccessToken("redis_user1@test.com");
        accessToken2 = getAccessToken("redis_user2@test.com");
    }

    @AfterEach
    void tearDown() {
        // ⭐️ 명시적 커밋으로 인해 트랜잭션이 종료된 경우에만 수동으로 핀포인트 청소 진행
        if (!TestTransaction.isActive()) {
            cleanAll();
        }
    }

    private void cleanAll() {
        try {
            if (testRoom != null) {
                String key = "chat:room:" + testRoom.getUuid() + ":messages";
                redisTemplate.delete(key);
            }
        } catch (Exception ignored) {}

        // ⭐️ [Targeted Deletion] 다른 테스트의 어드민 데이터 등을 침범하지 않고 오직 이 테스트가 생성한 데이터만 골라서 청소
        if (testRoom != null) {
            chatMessageRepository.deleteAllInBatch(chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(testRoom.getId()));
            chatRoomParticipantRepository.deleteAllInBatch(chatRoomParticipantRepository.findByChatRoomId(testRoom.getId()));
            chatRoomRepository.deleteById(testRoom.getId());
        }

        // 우리가 쓴 유저만 핀포인트 삭제 (메일 토큰은 삭제하지 않아도 다른 테스트 격리에 영향을 주지 않으므로 제외)
        Member redisUser1 = memberRepository.findByEmail("redis_user1@test.com");
        if (redisUser1 != null) {
            memberRepository.delete(redisUser1);
        }
        Member redisUser2 = memberRepository.findByEmail("redis_user2@test.com");
        if (redisUser2 != null) {
            memberRepository.delete(redisUser2);
        }
    }

    private void preVerifyEmail(String email) {
        EmailVerificationToken token = new EmailVerificationToken(email, "000000", 10);
        token.markVerified();
        emailVerificationTokenRepository.save(token);
    }

    private Member signupAndLogin(String email, String industry) throws Exception {
        preVerifyEmail(email);

        mvc.perform(
                post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\": \"%s\", \"password\": \"1234\", \"industry\": \"%s\", \"agreedToTerms\": true}", email, industry))

        );

        return memberRepository.findByEmail(email);
    }

    // 로그인 응답 바디에는 더 이상 토큰이 실리지 않는다(HttpOnly 쿠키로만 내려감).
    // 테스트에서 Bearer 헤더로 쓸 토큰은 실제 로그인 엔드포인트를 거칠 필요 없이
    // 발급 로직을 직접 호출해 받아온다.
    private String getAccessToken(String email) {
        return TestAccessTokenFactory.accessTokenFor(memberRepository, memberService, email);
    }

    @Test
    @DisplayName("메시지 전송 시 Redis ZSet 캐시에 비동기 자동 적재 검증")
    void t1() throws Exception {
        // When (API를 통한 메시지 발송)
        mvc.perform(
                post(String.format("/api/v1/rooms/%s/messages", testRoom.getUuid()))
                        .header("Authorization", "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"실시간 캐시 전송 테스트\"}")
        ).andExpect(status().isCreated());

        // ⭐️ 비동기 리스너(AFTER_COMMIT)가 정상 작동하도록 강제로 현재 테스트 트랜잭션을 커밋하여 완결시킵니다.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 비동기 적재 대기
        Thread.sleep(200);

        // Then (실제 레디스 메모리를 확인)
        String key = "chat:room:" + testRoom.getUuid() + ":messages";
        Set<String> jsonPayloads = redisTemplate.opsForZSet().range(key, 0, -1);

        assertThat(jsonPayloads).isNotNull().isNotEmpty();
        String json = jsonPayloads.iterator().next();
        RedisChatMessageDto dto = Ut.json.objectMapper.readValue(json, RedisChatMessageDto.class);

        assertThat(dto.getContent()).isEqualTo("실시간 캐시 전송 테스트");
        assertThat(dto.getSenderNickname()).isEqualTo("익명의 동료");
    }

    @Test
    @DisplayName("메시지 조회 시 캐시 히트 성공 검증 (DB 쿼리 타지 않고 캐시 반환)")
    void t2() throws Exception {
        // Given (레디스에 캐시를 미리 직접 수동 주입해 둠)
        String key = "chat:room:" + testRoom.getUuid() + ":messages";
        RedisChatMessageDto cachedDto = new RedisChatMessageDto(
                UUID.randomUUID(),
                testRoom.getUuid(),
                "가짜닉네임",
                UUID.randomUUID(),
                "레디스 캐시 전용 메시지",
                java.time.LocalDateTime.now()
        );

        String json = Ut.json.toString(cachedDto);
        long score = java.sql.Timestamp.valueOf(cachedDto.getCreatedAt()).getTime();
        redisTemplate.opsForZSet().add(key, json, score);

        // When (대화 조회 API 호출)
        ResultActions resultActions = mvc.perform(
                get(String.format("/api/v1/rooms/%s/messages", testRoom.getUuid()))
                        .header("Authorization", "Bearer " + accessToken1)
        ).andDo(print());

        // Then (DB에 없는 "가짜닉네임"과 "레디스 캐시 전용 메시지"가 제대로 서빙되었는지 확인 - Cache Hit 입증)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].senderNickname").value("가짜닉네임"))
                .andExpect(jsonPath("$.data[0].content").value("레디스 캐시 전용 메시지"));
    }

    @Test
    @DisplayName("대화방 종료 시 레디스 캐시 즉시 소멸(Evict) 검증")
    void t3() throws Exception {
        // Given (메시지를 하나 전송하여 레디스에 캐시 키를 생성해 둠)
        mvc.perform(
                post(String.format("/api/v1/rooms/%s/messages", testRoom.getUuid()))
                        .header("Authorization", "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"캐시 삭제 확인용\"}")
        ).andExpect(status().isCreated());

        // ⭐️ 비동기 리스너가 레디스에 적재할 수 있도록 명시적 커밋 처리
        TestTransaction.flagForCommit();
        TestTransaction.end();

        Thread.sleep(200);
        String key = "chat:room:" + testRoom.getUuid() + ":messages";
        assertThat(redisTemplate.hasKey(key)).isTrue(); // 방 닫기 전엔 캐시 키가 반드시 생존해 있어야 함

        // ⭐️ 새 트랜잭션을 수동으로 시작하지 않고, 방 종료 API를 직접 때려 delete 검증
        mvc.perform(
                patch(String.format("/api/v1/rooms/%s", testRoom.getUuid()))
                        .header("Authorization", "Bearer " + accessToken1)
        ).andExpect(status().isOk());

        // Then (종료 성공 즉시 레디스에서 키가 완전히 소멸되었는지 검증)
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    @DisplayName("[예외/Fallback] Redis가 완전히 다운되었어도 API가 200 OK로 MySQL DB 데이터를 정상 반환하는지 검증")
    void t4() throws Exception {
        // Given (DB에만 대화 기록을 수동으로 미리 저장해 둠)
        chatMessageService.sendMessage(testRoom.getId(), testMember1, "DB에만 저장된 메시지");

        // ⭐️ Redis가 에러난 상황을 시뮬레이션하기 위해 opsForZSet() 호출 시 ConnectionFailureException 예외를 뿜도록 모킹
        ZSetOperations<String, String> mockZSet = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(mockZSet);
        when(mockZSet.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new RedisConnectionFailureException("Redis Connection Refused"));
        when(mockZSet.range(anyString(), anyLong(), anyLong()))
                .thenThrow(new RedisConnectionFailureException("Redis Connection Refused"));

        // When (조회 API 호출 - 레디스가 다운된 척하는 환경)
        ResultActions resultActions = mvc.perform(
                get(String.format("/api/v1/rooms/%s/messages", testRoom.getUuid()))
                        .header("Authorization", "Bearer " + accessToken1)
        ).andDo(print());

        // Then (서버가 뻗지 않고 200 OK를 유지하며 MySQL DB 원본 메시지를 성공적으로 서빙했는지 검증)
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("DB에만 저장된 메시지"));
    }

    @Test
    @DisplayName("[예외/Self-Healing] 비동기 ZSet 적재 실패 시, 캐시 오염을 막기 위해 기존 캐시를 즉시 폭파(delete)하는 자가치유 작동 검증")
    void t5() throws Exception {
        // Given (정상적인 캐시 데이터가 이미 있는 상태)
        String key = "chat:room:" + testRoom.getUuid() + ":messages";
        redisTemplate.opsForZSet().add(key, "{\"content\":\"기존 캐시\"}", 1.0);

        // ⭐️ Redis ZSet에 데이터 추가(ZADD) 시점에 강제로 통신 장애가 발생하는 것처럼 모킹
        ZSetOperations<String, String> mockZSet = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(mockZSet);
        when(mockZSet.add(anyString(), anyString(), anyDouble()))
                .thenThrow(new RedisConnectionFailureException("Redis Connection Failed at ZADD"));

        // When (API로 새로운 메시지를 발송하여 이벤트를 유발함)
        mvc.perform(
                post(String.format("/api/v1/rooms/%s/messages", testRoom.getUuid()))
                        .header("Authorization", "Bearer " + accessToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"에러 발생용 메시지\"}")
        ).andExpect(status().isCreated());

        // ⭐️ 비동기 리스너 트리거를 위해 트랜잭션 강제 종료
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 비동기 자가 치유(Delete)가 일어날 시간을 대기
        Thread.sleep(200);

        // Then (캐시 오염을 막기 위해 핸들러의 catch 블록이 작동하여 기존의 ZSet key 자체를 완전히 delete 해두었는지 확인)
        assertThat(redisTemplate.hasKey(key)).isFalse(); // 백지 상태로 소멸되어 있어야 성공!
    }
}
