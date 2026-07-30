package com.back.global.security.oauth2;

import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.exception.ServiceException;
import com.back.global.rq.Rq;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final MemberService memberService;
    private final Rq rq;

    @Value("${custom.frontendBaseUrl:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${custom.accessToken.expirationSeconds}")
    private int accessTokenExpirationSeconds;
    @Value("${custom.refreshToken.expirationSeconds}")
    private int refreshTokenExpirationSeconds;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        UUID memberId = oAuth2User.getMemberId();
        Member member = memberService.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));
        String accessToken = memberService.genAccessToken(member);
        UUID refreshToken = memberService.genRefreshToken(member);
        rq.setCookie("accessToken", accessToken, accessTokenExpirationSeconds);
        rq.setCookie("refreshToken", refreshToken.toString(), refreshTokenExpirationSeconds);
        response.sendRedirect(frontendBaseUrl + "/oauth/callback");
    }
}
