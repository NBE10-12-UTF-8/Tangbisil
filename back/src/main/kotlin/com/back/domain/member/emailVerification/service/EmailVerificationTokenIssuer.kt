package com.back.domain.member.emailVerification.service

import com.back.domain.member.emailVerification.entity.EmailVerificationToken
import com.back.domain.member.emailVerification.repository.EmailVerificationTokenRepository
import com.back.domain.member.member.repository.MemberRepository
import com.back.global.exception.ServiceException
import com.back.global.util.EmailNormalizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime

@Component
class EmailVerificationTokenIssuer(
    private val tokenRepository: EmailVerificationTokenRepository,
    private val memberRepository: MemberRepository
) {
    @Value("\${custom.email.verification.code-expiration-minutes}")
    private var expirationMinutes: Int = 0

    companion object {
        private val RANDOM = SecureRandom()
    }

    // 순수 DB 트랜잭션. 외부 API 호출은 포함하지 않는다.
    @Transactional
    fun issue(email: String): String {
        val normalizedEmail = EmailNormalizer.normalize(email)

        if (memberRepository.findByEmail(normalizedEmail) != null) {
            throw ServiceException("409-1", "이미 가입된 이메일입니다.")
        }

        tokenRepository.findTopByEmailOrderByCreatedAtDesc(normalizedEmail)?.let { prev ->
            if (prev.createdAt.isAfter(LocalDateTime.now().minusSeconds(60))) {
                throw ServiceException("429-1", "잠시 후 다시 시도해주세요.")
            }
        }

        tokenRepository.deleteByEmail(normalizedEmail)

        val code = String.format("%06d", RANDOM.nextInt(1_000_000))
        tokenRepository.save(EmailVerificationToken(normalizedEmail, code, expirationMinutes))

        return code
    }

    @Transactional
    fun deleteToken(email: String) {
        tokenRepository.deleteByEmail(email)
    }
}
