package com.back.global.security;

import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.service.MemberService;
import com.back.global.exception.ServiceException;
import com.back.global.rq.Rq;
import com.back.global.rsData.RsData;
import com.back.standard.util.Ut;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationFilter extends OncePerRequestFilter {
    private final MemberService memberService;
    private final Rq rq;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            work(request, response, filterChain);
        } catch (ServiceException e) {
            RsData<Void> rsData = e.getRsData();
            response.setContentType("application/json");
            response.setStatus(rsData.statusCode());
            response.getWriter().write(
                    Ut.json.toString(rsData)
            );
        }
    }

    private void work(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (List.of("/api/v1/members/login", "/api/v1/members/signup").contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = "";
        String headerAuthorization = rq.getHeader("Authorization", "");

        if (!headerAuthorization.isBlank()) {
            if (!headerAuthorization.startsWith("Bearer "))
                throw new ServiceException("401-2", "Authorization 헤더가 Bearer 형식이 아닙니다.");

            accessToken = headerAuthorization.substring(7);
        } else {
            accessToken = rq.getCookieValue("accessToken", "");
        }

        if (accessToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, Object> payload = memberService.payload(accessToken);

        if (payload == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID id = (UUID) payload.get("id");
        String email = (String) payload.get("email");
        String role = (String) payload.get("role");

        // 실시간 DB 정지 조회 및 차단 가드 추가
        Member dbMember = memberService.findById(id)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));

        if (dbMember.isSuspended() && !isAllowedForSuspended(request)) {
            throw new ServiceException("403-1", "정지된 계정입니다. 내 정보 조회와 로그아웃만 가능합니다.");
        }

        Member member = new Member(id, email, role);

        UserDetails user = new SecurityUser(
                member.getId(),
                member.getEmail(),
                member.getAuthorities()
        );

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user,
                user.getPassword(),
                user.getAuthorities()
        );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    // 정지된 회원이 예외적으로 접근 가능한 경로/메서드 화이트리스트.
    // "내 정보 조회"와 로그아웃만 허용하고, 산업군 수정/탈퇴를 포함한 나머지는 전부 차단한다.
    private boolean isAllowedForSuspended(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (uri.equals("/api/v1/members/logout") && method.equals("POST")) {
            return true;
        }
        if (uri.equals("/api/v1/members/me") && method.equals("GET")) {
            return true;
        }
        return false;
    }
}