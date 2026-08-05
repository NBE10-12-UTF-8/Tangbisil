package com.back.global.webSocket;

import com.back.domain.member.member.service.MemberService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

// SockJS 핸드셰이크는 여전히 평범한 HTTP 요청이라 accessToken 쿠키가 자동으로 실려 온다.
// 여기서 미리 검증해 세션 attributes에 심어두면, STOMP CONNECT 프레임에 Authorization
// 헤더가 없어도(JS가 토큰을 들고 있지 않아도) StompAuthChannelInterceptor가 인증할 수 있다.
// 쿠키가 없거나 유효하지 않으면 그냥 통과시키고, 최종 인증 여부는 CONNECT 단계에서 판단한다.
@Component
@RequiredArgsConstructor
public class CookieHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CookieHandshakeInterceptor.class);

    private final MemberService memberService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return true;
        }

        String token = extractAccessTokenCookie(servletRequest.getServletRequest());
        if (token == null) {
            log.debug("WebSocket 핸드셰이크에 accessToken 쿠키가 없음 - CONNECT 단계의 헤더 인증으로 폴백");
            return true;
        }

        Map<String, Object> payload = memberService.payload(token);
        if (payload == null) {
            log.debug("WebSocket 핸드셰이크의 accessToken 쿠키가 만료·위조됨 - CONNECT 단계의 헤더 인증으로 폴백");
            return true;
        }

        Object rawId = payload.get("id");
        if (rawId == null) {
            log.debug("WebSocket 핸드셰이크 토큰 payload에 id 클레임이 없음 - CONNECT 단계의 헤더 인증으로 폴백");
            return true;
        }

        Object rawRole = payload.get("role");
        if (rawRole == null) {
            log.debug("WebSocket 핸드셰이크 토큰 payload에 role 클레임이 없음 - CONNECT 단계의 헤더 인증으로 폴백");
            return true;
        }

        try {
            UUID id = (rawId instanceof UUID u) ? u : UUID.fromString(rawId.toString());
            attributes.put("memberId", id);
            attributes.put("role", rawRole);
        } catch (IllegalArgumentException e) {
            log.debug("WebSocket 핸드셰이크 토큰의 id 클레임이 UUID 형식이 아님 - CONNECT 단계의 헤더 인증으로 폴백");
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractAccessTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("accessToken".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
