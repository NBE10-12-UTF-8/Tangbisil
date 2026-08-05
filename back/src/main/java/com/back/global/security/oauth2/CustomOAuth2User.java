package com.back.global.security.oauth2;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {
    private final Long memberId;
    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String nameAttributeKey;

    public CustomOAuth2User(
            Long memberId,
            Map<String, Object> attributes,
            Collection<? extends GrantedAuthority> authorities,
            String nameAttributeKey
    ) {
        this.memberId = memberId;
        this.attributes = attributes;
        this.authorities = authorities;
        this.nameAttributeKey = nameAttributeKey;
    }

    public Long getMemberId() {
        return memberId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return String.valueOf(attributes.get(nameAttributeKey));
    }
}