package com.back.domain.member.emailVerification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "email_verification_token")
class EmailVerificationToken(
    email: String,
    code: String,
    expirationMinutes: Int
) {
    companion object {
        private const val MAX_ATTEMPTS = 5
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    var email: String = email
        protected set

    var code: String = code
        protected set

    // "is" 접두사가 없으면 Kotlin이 getVerified()를 생성해 Java의 isVerified() 호출부와 안 맞는다.
    // 단, DB 컬럼명은 원래 Java 필드명(verified) 그대로라 Hibernate의 기본 네이밍 전략
    // (is_verified)과 어긋나 명시 지정이 필요하다.
    @Column(name = "verified")
    var isVerified: Boolean = false
        protected set

    var failedAttempts: Int = 0
        protected set

    var expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(expirationMinutes.toLong())
        protected set

    var createdAt: LocalDateTime = LocalDateTime.now()
        protected set

    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiresAt)

    fun isBlocked(): Boolean = failedAttempts >= MAX_ATTEMPTS

    // 코드가 틀리면 실패 횟수를 증가시킨다. 호출부에서 isBlocked()를 먼저 확인해야 한다.
    fun matches(inputCode: String): Boolean {
        val matched = code == inputCode
        if (!matched) {
            failedAttempts++
        }
        return matched
    }

    fun markVerified() {
        isVerified = true
    }
}
