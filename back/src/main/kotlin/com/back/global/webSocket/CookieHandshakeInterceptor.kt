package com.back.global.webSocket

import com.back.domain.member.member.service.MemberService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.util.UUID

// SockJS 핸드셰이크는 여전히 평범한 HTTP 요청이라 accessToken 쿠키가 자동으로 실려 온다.
// 여기서 미리 검증해 세션 attributes에 심어두면, STOMP CONNECT 프레임에 Authorization
// 헤더가 없어도(JS가 토큰을 들고 있지 않아도) StompAuthChannelInterceptor가 인증할 수 있다.
// 쿠키가 없거나 유효하지 않으면 그냥 통과시키고, 최종 인증 여부는 CONNECT 단계에서 판단한다.
@Component
class CookieHandshakeInterceptor(
    private val memberService: MemberService
) : HandshakeInterceptor {
    companion object {
        private val log = LoggerFactory.getLogger(CookieHandshakeInterceptor::class.java)
    }

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        if (request !is ServletServerHttpRequest) {
            return true
        }

        val token = extractAccessTokenCookie(request.servletRequest)
        if (token == null) {
            log.debug("WebSocket 핸드셰이크에 accessToken 쿠키가 없음 - CONNECT 단계의 헤더 인증으로 폴백")
            return true
        }

        val payload = memberService.payload(token)
        if (payload == null) {
            log.debug("WebSocket 핸드셰이크의 accessToken 쿠키가 만료·위조됨 - CONNECT 단계의 헤더 인증으로 폴백")
            return true
        }

        val rawId = payload["id"]
        if (rawId == null) {
            log.debug("WebSocket 핸드셰이크 토큰 payload에 id 클레임이 없음 - CONNECT 단계의 헤더 인증으로 폴백")
            return true
        }

        val rawRole = payload["role"]
        if (rawRole == null) {
            log.debug("WebSocket 핸드셰이크 토큰 payload에 role 클레임이 없음 - CONNECT 단계의 헤더 인증으로 폴백")
            return true
        }

        try {
            val id = if (rawId is UUID) rawId else UUID.fromString(rawId.toString())
            attributes["memberId"] = id
            attributes["role"] = rawRole
        } catch (e: IllegalArgumentException) {
            log.debug("WebSocket 핸드셰이크 토큰의 id 클레임이 UUID 형식이 아님 - CONNECT 단계의 헤더 인증으로 폴백")
        }
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
    }

    private fun extractAccessTokenCookie(request: HttpServletRequest): String? {
        val cookies = request.cookies ?: return null
        for (cookie in cookies) {
            if (cookie.name == "accessToken" && !cookie.value.isNullOrBlank()) {
                return cookie.value
            }
        }
        return null
    }
}
