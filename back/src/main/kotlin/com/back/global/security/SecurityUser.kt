package com.back.global.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User
import java.util.UUID

class SecurityUser(
    val id: Long,
    val uuid: UUID,
    email: String,
    authorities: Collection<GrantedAuthority>
) : User(email, "", authorities)
