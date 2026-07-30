package com.back.global.security.oauth2.userinfo;

import com.back.domain.member.member.entity.AuthProvider;

public interface OAuth2UserInfo {
    String getProviderId();
    AuthProvider getProvider();
    String getEmail();
}
