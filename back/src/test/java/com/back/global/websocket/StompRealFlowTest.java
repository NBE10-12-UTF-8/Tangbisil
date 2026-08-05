package com.back.global.websocket;

import com.back.domain.chat.chatRoom.entity.ChatRoom;
import com.back.domain.chat.chatRoom.entity.ChatRoomStatus;
import com.back.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.back.domain.chat.chatRoom.service.ChatRoomService;
import com.back.domain.chat.chatRoomParticipant.entity.ChatRoomParticipant;
import com.back.domain.chat.chatRoomParticipant.repository.ChatRoomParticipantRepository;
import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.back.domain.member.member.entity.Industry.IT;
import static org.assertj.core.api.Assertions.assertThat;

// 채팅 WebSocket 인증/구독이 실제 STOMP 클라이언트로 끝까지 정상 동작하는지 검증하는 회귀 테스트.
// mock 기반 StompAuthChannelInterceptorTest와 달리 실제 JWT 발급/파싱, 실제 DB 조회를 전부 거친다.
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class StompRealFlowTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Autowired
    private ChatRoomService chatRoomService;

    @Test
    @DisplayName("실제 STOMP CONNECT + SUBSCRIBE 흐름이 에러 없이 성공한다")
    void t1() throws Exception {
        Member member = memberService.joinWithoutEmailVerification("stomp-real@test.com", "1234", IT, "USER");
        String accessToken = memberService.genAccessToken(member);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getUuid();
        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, member, "익명의 동료"));

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        CompletableFuture<Void> subscribedFuture = new CompletableFuture<>();

        StompFrameHandler noopFrameHandler = new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        };

        StompSessionHandler handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                session.subscribe("/user/queue/rooms/" + roomId, noopFrameHandler);
                session.subscribe("/user/queue/errors", noopFrameHandler);
                subscribedFuture.complete(null);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errorFuture.complete(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorFuture.complete(new IllegalStateException("예상치 못한 ERROR 프레임: " + headers));
            }
        };

        stompClient.connectAsync("ws://localhost:" + port + "/ws/websocket",
                (WebSocketHttpHeaders) null, connectHeaders, handler);

        subscribedFuture.get(10, TimeUnit.SECONDS);

        // 구독 요청은 fire-and-forget이라 ack을 기다리지 않는다 - 뒤늦게 오는 에러 프레임을 잡기 위해 잠시 더 대기
        try {
            Throwable ex = errorFuture.get(3, TimeUnit.SECONDS);
            throw new AssertionError("STOMP 에러 발생: " + ex, ex);
        } catch (java.util.concurrent.TimeoutException expected) {
            // 3초 안에 에러가 없으면 정상
        }
    }

    @Test
    @DisplayName("상대가 채팅방을 종료하면 실시간으로 WebSocket 알림을 받는다")
    void t2() throws Exception {
        Member memberA = memberService.joinWithoutEmailVerification("room-closed-a@test.com", "1234", IT, "USER");
        Member memberB = memberService.joinWithoutEmailVerification("room-closed-b@test.com", "1234", IT, "USER");
        String accessTokenA = memberService.genAccessToken(memberA);

        ChatRoom chatRoom = chatRoomRepository.save(new ChatRoom(ChatRoomStatus.ACTIVE, 2));
        UUID roomId = chatRoom.getUuid();
        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, memberA, "익명의 동료1"));
        chatRoomParticipantRepository.save(new ChatRoomParticipant(chatRoom, memberB, "익명의 동료2"));

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessTokenA);

        CompletableFuture<byte[]> roomFrameFuture = new CompletableFuture<>();
        CompletableFuture<Void> subscribedFuture = new CompletableFuture<>();

        StompSessionHandler handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                session.subscribe("/user/queue/rooms/" + roomId, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        roomFrameFuture.complete((byte[]) payload);
                    }
                });
                subscribedFuture.complete(null);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                roomFrameFuture.completeExceptionally(exception);
            }
        };

        stompClient.connectAsync("ws://localhost:" + port + "/ws/websocket",
                (WebSocketHttpHeaders) null, connectHeaders, handler);
        subscribedFuture.get(10, TimeUnit.SECONDS);

        // When - 상대(B)가 채팅방을 종료
        chatRoomService.closeChatRoom(roomId, memberB);

        // Then - A가 실시간으로 종료 알림을 받는다 (messageId가 없는 프레임)
        byte[] payload = roomFrameFuture.get(10, TimeUnit.SECONDS);
        JsonNode json = new ObjectMapper().readTree(payload);
        assertThat(json.has("messageId")).isFalse();
        assertThat(json.get("type").asText()).isEqualTo("ROOM_CLOSED");
    }
}
