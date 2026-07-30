package com.back.global.security.oauth2.userinfo;

import com.back.domain.member.member.entity.AuthProvider;

import java.util.Map;

public class KakaoUserInfo implements OAuth2UserInfo{
    private final Map<String, Object> attributes;
    public KakaoUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    public String getProviderId() {
        Object id = attributes.get("id");
        if (id == null) {
            throw new IllegalArgumentException("Kakao Provider ID (id)가 존재하지 않습니다.");
        }
        return String.valueOf(id);
    }
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
    }
    public String getEmail() {
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        if (kakaoAccount == null) {
            return null;
        }
        return (String) kakaoAccount.get("email");
    }
}
