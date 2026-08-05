package com.back.domain.member.member.service;

import com.back.domain.member.member.entity.Member;
import com.back.standard.util.Ut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthTokenService {
    @Value("${custom.accessToken.expirationSeconds}")
    private int expireSeconds;

    @Value("${custom.jwt.secretKey}")
    private String secret;

    public String genAccessToken(Member member) {
        // JWT엔 내부 PK(Long)가 아니라 외부 공개 식별자(uuid)를 싣는다 — 순번 노출 방지.
        String id = member.getUuid().toString();
        String email = member.getEmail();
        String role = member.getRole();

        return Ut.jwt.toString(
                secret,
                expireSeconds,
                Map.of("id", id, "email", email, "role", role)
        );
    }

    public Map<String, Object> payload(String accessToken) {
        Map<String, Object> parsedPayload = Ut.jwt.payload(secret, accessToken);

        if (parsedPayload == null) return null;

        UUID uuid = UUID.fromString((String) parsedPayload.get("id"));
        String email = (String) parsedPayload.get("email");
        String role = (String) parsedPayload.get("role");

        return Map.of("id", uuid, "email", email, "role", role);
    }
}