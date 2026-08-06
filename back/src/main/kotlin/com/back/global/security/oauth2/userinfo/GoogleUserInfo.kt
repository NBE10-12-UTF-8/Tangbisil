package com.back.global.security.oauth2.userinfo

import com.back.domain.member.member.entity.AuthProvider

class GoogleUserInfo(
    private val attributes: Map<String, Any?>
) : OAuth2UserInfo {
    override fun getProviderId(): String {
        val sub = attributes["sub"] ?: throw IllegalArgumentException("Google Provider ID (sub)가 존재하지 않습니다.")
        return sub.toString()
    }

    override fun getEmail(): String? = attributes["email"] as String?

    override fun getProvider(): AuthProvider = AuthProvider.GOOGLE
}
