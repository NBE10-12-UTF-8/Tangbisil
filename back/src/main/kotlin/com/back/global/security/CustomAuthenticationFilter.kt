package com.back.global.security

import com.back.domain.member.member.entity.Member
import com.back.domain.member.member.service.MemberService
import com.back.global.exception.ServiceException
import com.back.global.rq.Rq
import com.back.standard.util.Ut
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class CustomAuthenticationFilter(
    private val memberService: MemberService,
    private val rq: Rq
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        try {
            work(request, response, filterChain)
        } catch (e: ServiceException) {
            val rsData = e.rsData
            response.contentType = "application/json"
            response.status = rsData.statusCode
            response.writer.write(Ut.json.toString(rsData))
        }
    }

    private fun work(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (!request.requestURI.startsWith("/api/")) {
            filterChain.doFilter(request, response)
            return
        }

        if (request.requestURI in listOf("/api/v1/members/login", "/api/v1/members/signup")) {
            filterChain.doFilter(request, response)
            return
        }

        val headerAuthorization = rq.getHeader("Authorization", "")

        val accessToken: String = if (headerAuthorization.isNotBlank()) {
            if (!headerAuthorization.startsWith("Bearer ")) {
                throw ServiceException("401-2", "Authorization 헤더가 Bearer 형식이 아닙니다.")
            }
            headerAuthorization.substring(7)
        } else {
            rq.getCookieValue("accessToken", "")
        }

        if (accessToken.isBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        val payload = memberService.payload(accessToken)

        if (payload == null) {
            filterChain.doFilter(request, response)
            return
        }

        val uuid = payload["id"] as UUID
        val email = payload["email"] as String
        val role = payload["role"] as String

        // 실시간 DB 정지 조회 및 차단 가드 추가 — 이 조회가 JWT의 공개 uuid를 내부 Long PK로
        // 바꿔주는 지점이라, 아래에서 별도 쿼리 없이 dbMember.getId()(Long)를 그대로 쓴다.
        val dbMember = memberService.findByUuid(uuid)
            .orElseThrow { ServiceException("404-1", "존재하지 않는 회원입니다.") }

        if (dbMember.isSuspended && !isAllowedForSuspended(request)) {
            throw ServiceException("403-1", "정지된 계정입니다. 내 정보 조회와 로그아웃만 가능합니다.")
        }

        val member = Member(dbMember.id!!, dbMember.uuid, email, role)

        val user = SecurityUser(
            member.id!!,
            member.uuid,
            member.email!!,
            member.getAuthorities()
        )

        val authentication = UsernamePasswordAuthenticationToken(
            user,
            user.password,
            user.authorities
        )

        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(request, response)
    }

    // 정지된 회원이 예외적으로 접근 가능한 경로/메서드 화이트리스트.
    // "내 정보 조회"와 로그아웃만 허용하고, 산업군 수정/탈퇴를 포함한 나머지는 전부 차단한다.
    private fun isAllowedForSuspended(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        val method = request.method

        if (uri == "/api/v1/members/logout" && method == "POST") {
            return true
        }
        if (uri == "/api/v1/members/me" && method == "GET") {
            return true
        }
        return false
    }
}
