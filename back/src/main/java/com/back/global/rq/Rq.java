package com.back.global.rq;

import com.back.domain.member.member.entity.Member;
import com.back.global.security.SecurityUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class Rq {
    // refreshToken은 /refresh 요청 딱 하나에서만 쓰는데 Path=/로 두면 모든 API 요청에
    // 매번 같이 실려 나간다. XSS가 터졌을 때 공격자가 만드는 요청에까지 30일짜리
    // refreshToken이 자동으로 따라붙는 걸 막기 위해, 이 쿠키만 그 엔드포인트로 스코프를 좁힌다.
    public static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/members/refresh";

    private final HttpServletRequest req;
    private final HttpServletResponse resp;

    // OAuth2 로그인은 프론트(tangbisil.kro.kr)가 아니라 백엔드(api.tangbisil.kro.kr)에서
    // 브라우저가 직접 리다이렉트를 거치므로, Domain을 안 정해주면 쿠키가 api.tangbisil.kro.kr에만
    // host-only로 스코프되어 tangbisil.kro.kr을 통한(Next.js 프록시) API 호출엔 안 실린다.
    // dev/test는 빈 문자열로 둬서 기존처럼 host-only(localhost) 쿠키를 유지한다.
    @Value("${custom.cookieDomain:}")
    private String cookieDomain = "";

    // 로컬 개발(HTTP)에서는 Secure 쿠키가 아예 안 실려 로그인이 막히므로 기본값은 false로 두고,
    // prod에서는 HTTPS만 쓰므로 true로 재정의한다.
    @Value("${custom.cookieSecure:false}")
    private boolean cookieSecure;

    public Member getActor() {
        return Optional.ofNullable(
                        SecurityContextHolder
                                .getContext()
                                .getAuthentication()
                )
                .map(Authentication::getPrincipal)
                .filter(principal -> principal instanceof SecurityUser)
                .map(principal -> (SecurityUser) principal)
                .map(securityUser -> {
                    String role = securityUser.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .map(auth -> auth.replace("ROLE_", ""))
                            .findFirst()
                            .orElse("USER");
                    return new Member(securityUser.getId(), securityUser.getUuid(), securityUser.getUsername(), role);
                })
                .orElse(null);
    }

    public String getHeader(String name, String defaultValue) {
        return Optional
                .ofNullable(req.getHeader(name))
                .filter(headerValue -> !headerValue.isBlank())
                .orElse(defaultValue);
    }

    public void setHeader(String name, String value) {
        if (value == null) value = "";

        if (value.isBlank()) {
            req.removeAttribute(name);
        } else {
            resp.setHeader(name, value);
        }
    }

    public String getCookieValue(String name, String defaultValue) {
        return Arrays.stream(Optional.ofNullable(req.getCookies()).orElse(new Cookie[0]))
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(defaultValue);
    }

    public void setCookie(String name, String value, int maxAge) {
        setCookie(name, value, maxAge, "/");
    }

    public void setCookie(String name, String value, int maxAge, String path) {
        if (value == null) value = "";

        Cookie cookie = new Cookie(name, value);
        cookie.setPath(path);
        cookie.setHttpOnly(true);

        if (!cookieDomain.isBlank()) {
            cookie.setDomain(cookieDomain);
        }
        cookie.setSecure(cookieSecure);
        cookie.setAttribute("SameSite", "Strict");

        if (value.isBlank()) cookie.setMaxAge(0);
        else cookie.setMaxAge(maxAge);

        resp.addCookie(cookie);
    }

    public void setCookie(String name, String value) {
        setCookie(name, value, 60 * 60 * 24 * 365);
    }

    public void deleteCookie(String name) {
        setCookie(name, null);
    }

    // 쿠키를 지우는 Set-Cookie도 원래 쿠키를 심을 때와 Path가 정확히 같아야 브라우저가
    // 같은 쿠키로 인식해서 지운다 - Path가 다르면 그냥 아무것도 없는 걸 지우는 셈이 되어
    // 원래 쿠키(refreshToken 등)가 그대로 남는다.
    public void deleteCookie(String name, String path) {
        setCookie(name, null, 0, path);
    }
}