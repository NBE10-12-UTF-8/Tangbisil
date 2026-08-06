package com.back.global.rq

import com.back.domain.member.member.entity.Member
import com.back.global.security.SecurityUser
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class Rq(
    private val req: HttpServletRequest,
    private val resp: HttpServletResponse
) {
    companion object {
        // refreshToken은 /refresh 요청 딱 하나에서만 쓰는데 Path=/로 두면 모든 API 요청에
        // 매번 같이 실려 나간다. XSS가 터졌을 때 공격자가 만드는 요청에까지 30일짜리
        // refreshToken이 자동으로 따라붙는 걸 막기 위해, 이 쿠키만 그 엔드포인트로 스코프를 좁힌다.
        const val REFRESH_TOKEN_COOKIE_PATH = "/api/v1/members/refresh"
    }

    // OAuth2 로그인은 프론트(tangbisil.kro.kr)가 아니라 백엔드(api.tangbisil.kro.kr)에서
    // 브라우저가 직접 리다이렉트를 거치므로, Domain을 안 정해주면 쿠키가 api.tangbisil.kro.kr에만
    // host-only로 스코프되어 tangbisil.kro.kr을 통한(Next.js 프록시) API 호출엔 안 실린다.
    // dev/test는 빈 문자열로 둬서 기존처럼 host-only(localhost) 쿠키를 유지한다.
    @Value("\${custom.cookieDomain:}")
    private var cookieDomain: String = ""

    // 로컬 개발(HTTP)에서는 Secure 쿠키가 아예 안 실려 로그인이 막히므로 기본값은 false로 두고,
    // prod에서는 HTTPS만 쓰므로 true로 재정의한다.
    @Value("\${custom.cookieSecure:false}")
    private var cookieSecure: Boolean = false

    val actor: Member?
        get() {
            val principal = SecurityContextHolder.getContext().authentication?.principal
            if (principal !is SecurityUser) {
                return null
            }
            val role = principal.authorities.asSequence()
                .map { it.authority!!.replace("ROLE_", "") }
                .firstOrNull() ?: "USER"
            return Member(principal.id, principal.uuid, principal.username, role)
        }

    fun getHeader(name: String, defaultValue: String): String {
        val headerValue = req.getHeader(name)
        return if (headerValue != null && headerValue.isNotBlank()) headerValue else defaultValue
    }

    fun setHeader(name: String, value: String?) {
        val resolvedValue = value ?: ""

        if (resolvedValue.isBlank()) {
            req.removeAttribute(name)
        } else {
            resp.setHeader(name, resolvedValue)
        }
    }

    fun getCookieValue(name: String, defaultValue: String): String =
        (req.cookies ?: emptyArray())
            .filter { it.name == name }
            .map { it.value }
            .firstOrNull { !it.isNullOrBlank() }
            ?: defaultValue

    fun setCookie(name: String, value: String?, maxAge: Int) {
        setCookie(name, value, maxAge, "/")
    }

    fun setCookie(name: String, value: String?, maxAge: Int, path: String) {
        val resolvedValue = value ?: ""

        val cookie = Cookie(name, resolvedValue)
        cookie.path = path
        cookie.isHttpOnly = true

        if (cookieDomain.isNotBlank()) {
            cookie.domain = cookieDomain
        }
        cookie.secure = cookieSecure
        cookie.setAttribute("SameSite", "Strict")

        if (resolvedValue.isBlank()) cookie.maxAge = 0
        else cookie.maxAge = maxAge

        resp.addCookie(cookie)
    }

    fun setCookie(name: String, value: String?) {
        setCookie(name, value, 60 * 60 * 24 * 365)
    }

    fun deleteCookie(name: String) {
        setCookie(name, null)
    }

    // 쿠키를 지우는 Set-Cookie도 원래 쿠키를 심을 때와 Path가 정확히 같아야 브라우저가
    // 같은 쿠키로 인식해서 지운다 - Path가 다르면 그냥 아무것도 없는 걸 지우는 셈이 되어
    // 원래 쿠키(refreshToken 등)가 그대로 남는다.
    fun deleteCookie(name: String, path: String) {
        setCookie(name, null, 0, path)
    }
}
