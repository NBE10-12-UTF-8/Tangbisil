package com.back.global.security.oauth2;

import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.rq.Rq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * OAuth2 1회용 code를 없애고, 로그인 성공 시점에 바로 쿠키를 심어서 리다이렉트하는
 * 새 동작을 규정하는 테스트. 아직 구현 전이라 컴파일이 안 될 수 있음 - 아래 힌트를 참고해서
 * OAuth2LoginSuccessHandler를 이 테스트가 통과하도록 고치면 됨.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    private static final String FRONTEND_BASE_URL = "https://tangbisil.kro.kr";

    @Mock
    private MemberService memberService;

    @Mock
    private Rq rq;

    @Test
    @DisplayName("로그인 성공 시 code 발급 없이 쿠키를 직접 심고, 파라미터 없는 URL로 리다이렉트한다")
    void t1() throws Exception {
        // Given
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "test@test.com", "USER");

        when(memberService.findById(memberId)).thenReturn(Optional.of(member));
        when(memberService.genAccessToken(member)).thenReturn("access-token-value");
        when(memberService.genRefreshToken(member)).thenReturn(UUID.randomUUID());

        CustomOAuth2User principal = new CustomOAuth2User(memberId, Map.of(), List.of(), "id");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(memberService, rq);
        ReflectionTestUtils.setField(handler, "frontendBaseUrl", FRONTEND_BASE_URL);

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then - OAuthCodeStore를 거치지 않고, 발급받은 토큰을 Rq를 통해 쿠키로 직접 심어야 한다
        verify(rq).setCookie(eq("accessToken"), eq("access-token-value"), anyInt());
        verify(rq).setCookie(eq("refreshToken"), anyString(), anyInt());

        // Then - 리다이렉트 URL에는 code도, 토큰 원문도 노출되면 안 된다
        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).isEqualTo(FRONTEND_BASE_URL + "/oauth/callback");
    }
}
