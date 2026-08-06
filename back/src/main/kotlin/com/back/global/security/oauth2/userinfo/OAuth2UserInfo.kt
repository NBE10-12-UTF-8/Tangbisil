package com.back.global.security.oauth2.userinfo

import com.back.domain.member.member.entity.AuthProvider

interface OAuth2UserInfo {
    fun getProviderId(): String
    fun getProvider(): AuthProvider
    fun getEmail(): String?
}
