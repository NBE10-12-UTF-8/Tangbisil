package com.back.global.websocket;

import com.back.domain.member.member.service.MemberService;
import com.back.global.webSocket.CookieHandshakeInterceptor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookieHandshakeInterceptorTest {

    @Mock
    MemberService memberService;

    @InjectMocks
    CookieHandshakeInterceptor interceptor;

    private ServerHttpRequest requestWithCookies(Cookie... cookies) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getCookies()).thenReturn(cookies);
        return new ServletServerHttpRequest(servletRequest);
    }

    @Test
    @DisplayName("유효한 accessToken 쿠키가 있으면 세션 attributes에 memberId·role을 채운다")
    void t1() {
        UUID memberId = UUID.randomUUID();
        when(memberService.payload("valid-token")).thenReturn(Map.of(
                "id", memberId.toString(),
                "role", "USER"
        ));

        ServerHttpRequest request = requestWithCookies(new Cookie("accessToken", "valid-token"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, mock(org.springframework.http.server.ServerHttpResponse.class), null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("memberId")).isEqualTo(memberId);
        assertThat(attributes.get("role")).isEqualTo("USER");
    }

    @Test
    @DisplayName("accessToken 쿠키가 없으면 attributes를 채우지 않고 통과시킨다")
    void t2() {
        ServerHttpRequest request = requestWithCookies();
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, mock(org.springframework.http.server.ServerHttpResponse.class), null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("만료·위조 토큰(payload null)이면 attributes를 채우지 않고 통과시킨다")
    void t3() {
        when(memberService.payload("bad-token")).thenReturn(null);

        ServerHttpRequest request = requestWithCookies(new Cookie("accessToken", "bad-token"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, mock(org.springframework.http.server.ServerHttpResponse.class), null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("payload에 id 클레임이 없으면 NPE 없이 attributes를 채우지 않고 통과시킨다")
    void t5() {
        when(memberService.payload("no-id-token")).thenReturn(Map.of("role", "USER"));

        ServerHttpRequest request = requestWithCookies(new Cookie("accessToken", "no-id-token"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, mock(org.springframework.http.server.ServerHttpResponse.class), null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("다른 이름의 쿠키만 있으면 attributes를 채우지 않는다")
    void t4() {
        ServerHttpRequest request = requestWithCookies(new Cookie("refreshToken", "some-value"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, mock(org.springframework.http.server.ServerHttpResponse.class), null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes).isEmpty();
    }
}
