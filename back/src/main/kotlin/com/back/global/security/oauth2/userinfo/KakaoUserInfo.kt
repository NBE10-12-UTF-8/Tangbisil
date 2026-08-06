package com.back.global.security.oauth2.userinfo

import com.back.domain.member.member.entity.AuthProvider

class KakaoUserInfo(
    private val attributes: Map<String, Any?>
) : OAuth2UserInfo {
    override fun getProviderId(): String {
        val id = attributes["id"] ?: throw IllegalArgumentException("Kakao Provider ID (id)가 존재하지 않습니다.")
        return id.toString()
    }

    override fun getProvider(): AuthProvider = AuthProvider.KAKAO

    @Suppress("UNCHECKED_CAST")
    override fun getEmail(): String? {
        val kakaoAccount = attributes["kakao_account"] as Map<String, Any?>? ?: return null
        return kakaoAccount["email"] as String?
    }
}
