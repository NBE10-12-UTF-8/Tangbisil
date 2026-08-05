package com.back.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

@Getter
public class SecurityUser extends User {
    private final Long id;
    private final UUID uuid;

    public SecurityUser(
            Long id,
            UUID uuid,
            String email,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(email, "", authorities);
        this.id = id;
        this.uuid = uuid;
    }
}