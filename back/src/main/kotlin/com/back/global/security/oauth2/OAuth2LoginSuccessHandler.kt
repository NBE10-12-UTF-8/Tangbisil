package com.back.global.security.oauth2

import com.back.domain.member.member.service.MemberService
import com.back.global.exception.ServiceException
import com.back.global.rq.Rq
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    private val memberService: MemberService,
    private val rq: Rq
) : AuthenticationSuccessHandler {

    @Value("\${custom.frontendBaseUrl:http://localhost:3000}")
    private lateinit var frontendBaseUrl: String

    @Value("\${custom.accessToken.expirationSeconds}")
    private var accessTokenExpirationSeconds: Int = 0

    @Value("\${custom.refreshToken.expirationSeconds}")
    private var refreshTokenExpirationSeconds: Int = 0

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oAuth2User = authentication.principal as CustomOAuth2User
        val memberId = oAuth2User.memberId
        val member = memberService.findById(memberId)
            .orElseThrow { ServiceException("404-1", "존재하지 않는 회원입니다.") }
        val accessToken = memberService.genAccessToken(member)
        val refreshToken = memberService.genRefreshToken(member)
        rq.setCookie("accessToken", accessToken, accessTokenExpirationSeconds)
        rq.setCookie("refreshToken", refreshToken.toString(), refreshTokenExpirationSeconds, Rq.REFRESH_TOKEN_COOKIE_PATH)
        response.sendRedirect("$frontendBaseUrl/oauth/callback")
    }
}
