package com.back.global.security.oauth2.userinfo;

import com.back.domain.member.member.entity.AuthProvider;

import java.util.Map;

public class GoogleUserInfo implements OAuth2UserInfo{
    private final Map<String, Object> attributes;
    public GoogleUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    public String getProviderId() {
        Object sub = attributes.get("sub");
        if (sub == null) {
            throw new IllegalArgumentException("Google Provider ID (sub)가 존재하지 않습니다.");
        }
        return String.valueOf(sub);
    }
    public String getEmail() {
        return (String) attributes.get("email");
    }
    public AuthProvider getProvider() {
        return AuthProvider.GOOGLE;
    }
}
