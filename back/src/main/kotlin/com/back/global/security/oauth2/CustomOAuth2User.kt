package com.back.global.security.oauth2

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class CustomOAuth2User(
    val memberId: Long,
    private val attributes: Map<String, Any>,
    private val authorities: Collection<GrantedAuthority>,
    private val nameAttributeKey: String
) : OAuth2User {
    override fun getAttributes(): Map<String, Any> = attributes

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun getName(): String = attributes[nameAttributeKey].toString()
}
