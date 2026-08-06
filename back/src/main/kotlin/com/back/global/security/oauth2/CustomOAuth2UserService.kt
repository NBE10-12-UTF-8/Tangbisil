package com.back.global.security.oauth2

import com.back.domain.member.member.service.MemberService
import com.back.global.security.oauth2.userinfo.GoogleUserInfo
import com.back.global.security.oauth2.userinfo.KakaoUserInfo
import com.back.global.security.oauth2.userinfo.OAuth2UserInfo
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
    private val memberService: MemberService
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = delegate.loadUser(userRequest)
        val attributes = oAuth2User.attributes

        val registrationId = userRequest.clientRegistration.registrationId

        val userInfo: OAuth2UserInfo = if (registrationId == "kakao") {
            KakaoUserInfo(attributes)
        } else {
            GoogleUserInfo(attributes)
        }

        val member = memberService.findOrCreateOAuthMember(
            userInfo.getEmail()!!, userInfo.getProvider(), userInfo.getProviderId()
        )
        val nameAttributeKey = userRequest.clientRegistration
            .providerDetails
            .userInfoEndpoint
            .userNameAttributeName!!

        return CustomOAuth2User(member.id!!, attributes, member.getAuthorities(), nameAttributeKey)
    }
}
