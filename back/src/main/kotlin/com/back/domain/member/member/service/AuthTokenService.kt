package com.back.domain.member.member.service

import com.back.domain.member.member.entity.Member
import com.back.standard.util.Ut
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthTokenService {
    @Value("\${custom.accessToken.expirationSeconds}")
    private var expireSeconds: Int = 0

    @Value("\${custom.jwt.secretKey}")
    private lateinit var secret: String

    fun genAccessToken(member: Member): String {
        // JWT엔 내부 PK(Long)가 아니라 외부 공개 식별자(uuid)를 싣는다 — 순번 노출 방지.
        val id = member.uuid.toString()
        val email = member.email
        val role = member.role

        return Ut.jwt.toString(
            secret,
            expireSeconds,
            mapOf("id" to id, "email" to email, "role" to role)
        )
    }

    fun payload(accessToken: String): Map<String, Any?>? {
        val parsedPayload = Ut.jwt.payload(secret, accessToken) ?: return null

        val uuid = UUID.fromString(parsedPayload["id"] as String)
        val email = parsedPayload["email"]
        val role = parsedPayload["role"]

        return mapOf("id" to uuid, "email" to email, "role" to role)
    }
}
