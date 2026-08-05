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
                    return new Member(securityUser.getId(), securityUser.getUsername(), role);
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
        if (value == null) value = "";

        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
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
}