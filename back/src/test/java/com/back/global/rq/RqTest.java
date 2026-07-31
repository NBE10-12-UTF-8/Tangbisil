package com.back.global.rq;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RqTest {

    @Test
    @DisplayName("cookieDomain이 설정되면 쿠키에 Domain이 그대로 반영된다 - api/프론트 서브도메인 간 쿠키 공유용")
    void t1() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Rq rq = new Rq(request, response);
        ReflectionTestUtils.setField(rq, "cookieDomain", ".tangbisil.kro.kr");

        rq.setCookie("accessToken", "token-value", 60);

        Cookie cookie = response.getCookie("accessToken");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getDomain()).isEqualTo(".tangbisil.kro.kr");
    }

    @Test
    @DisplayName("cookieDomain이 비어있으면(dev/test) 쿠키에 Domain을 지정하지 않는다 - host-only 유지")
    void t2() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Rq rq = new Rq(request, response);
        ReflectionTestUtils.setField(rq, "cookieDomain", "");

        rq.setCookie("accessToken", "token-value", 60);

        Cookie cookie = response.getCookie("accessToken");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getDomain()).isNullOrEmpty();
    }
}
