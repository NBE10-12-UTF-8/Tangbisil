package com.back.domain.member.passwordReset.service

import com.back.domain.member.member.repository.MemberRepository
import com.back.domain.member.passwordReset.entity.PasswordResetToken
import com.back.domain.member.passwordReset.repository.PasswordResetTokenRepository
import com.back.global.exception.ServiceException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime

@Component
class PasswordResetTokenIssuer(
    private val tokenRepository: PasswordResetTokenRepository,
    private val memberRepository: MemberRepository
) {
    @Value("\${custom.password-reset.code-expiration-minutes}")
    private var expirationMinutes: Int = 0

    companion object {
        private val RANDOM = SecureRandom()
    }

    data class IssueResult(val code: String, val memberExists: Boolean)

    // 회원이 존재할 때만 토큰을 발급하고 코드를 반환한다.
    // 존재하지 않으면 memberExists=false를 반환할 뿐, 예외를 던지지 않는다 (이메일 존재 여부 노출 방지).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun issue(email: String): IssueResult {
        tokenRepository.findTopByEmailOrderByCreatedAtDesc(email)?.let { prev ->
            if (prev.createdAt.isAfter(LocalDateTime.now().minusSeconds(60))) {
                throw ServiceException("429-1", "잠시 후 다시 시도해주세요.")
            }
        }

        tokenRepository.deleteByEmail(email)

        val code = String.format("%06d", RANDOM.nextInt(1_000_000))
        tokenRepository.save(PasswordResetToken(email, code, expirationMinutes))

        val memberExists = memberRepository.findByEmail(email) != null
        return IssueResult(code, memberExists)
    }

    @Transactional
    fun deleteToken(email: String) {
        tokenRepository.deleteByEmail(email)
    }
}
