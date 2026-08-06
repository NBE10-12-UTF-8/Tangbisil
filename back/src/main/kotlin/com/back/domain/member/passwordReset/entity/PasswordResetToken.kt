package com.back.domain.member.passwordReset.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "password_reset_token")
class PasswordResetToken(
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

    var failedAttempts: Int = 0
        protected set

    var expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(expirationMinutes.toLong())
        protected set

    var createdAt: LocalDateTime = LocalDateTime.now()
        protected set

    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiresAt)

    fun isBlocked(): Boolean = failedAttempts >= MAX_ATTEMPTS

    fun verifyCode(inputCode: String): Boolean {
        val matched = code == inputCode
        if (!matched) {
            failedAttempts++
        }
        return matched
    }
}
