package com.back.domain.member.emailVerification.service

import com.back.domain.member.emailVerification.repository.EmailVerificationTokenRepository
import com.back.global.email.ResendEmailService
import com.back.global.email.VerificationEmailTemplate
import com.back.global.exception.ServiceException
import com.back.global.util.EmailNormalizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class EmailVerificationService(
    private val tokenRepository: EmailVerificationTokenRepository,
    private val tokenIssuer: EmailVerificationTokenIssuer,
    private val resendEmailService: ResendEmailService
) {
    @Value("\${custom.email.verification.code-expiration-minutes}")
    private var expirationMinutes: Int = 0

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun sendVerificationCode(email: String) {
        val normalizedEmail = EmailNormalizer.normalize(email)
        val code = tokenIssuer.issue(normalizedEmail)

        val html = VerificationEmailTemplate.render(code, expirationMinutes)
        try {
            resendEmailService.send(normalizedEmail, "[탕비실] 이메일 인증 코드", html)
        } catch (e: RuntimeException) {
            tokenIssuer.deleteToken(normalizedEmail)
            throw e
        }
    }

    // failedAttempts 증가가 예외로 인해 롤백되지 않도록 noRollbackFor 지정
    @Transactional(noRollbackFor = [ServiceException::class])
    fun confirmVerificationCode(email: String, code: String) {
        val normalizedEmail = EmailNormalizer.normalize(email)

        val token = tokenRepository.findTopByEmailOrderByCreatedAtDesc(normalizedEmail)
            ?: throw ServiceException("400-1", "발송된 인증 코드가 없습니다.")

        if (token.isExpired()) {
            throw ServiceException("400-2", "인증 코드가 만료되었습니다.")
        }
        if (token.isBlocked()) {
            throw ServiceException("400-5", "인증 시도 횟수를 초과했습니다. 인증 코드를 다시 발송해주세요.")
        }
        if (!token.matches(code)) {
            throw ServiceException("400-3", "인증 코드가 일치하지 않습니다.")
        }

        token.markVerified()
    }

    @Transactional
    fun consumeVerifiedEmail(email: String) {
        val normalizedEmail = EmailNormalizer.normalize(email)

        val token = tokenRepository.findTopByEmailOrderByCreatedAtDesc(normalizedEmail)
            ?: throw ServiceException("400-4", "이메일 인증이 필요합니다.")

        if (!token.isVerified || token.isExpired()) {
            throw ServiceException("400-4", "이메일 인증이 필요하거나 만료되었습니다.")
        }

        tokenRepository.deleteByEmail(normalizedEmail)
    }
}
