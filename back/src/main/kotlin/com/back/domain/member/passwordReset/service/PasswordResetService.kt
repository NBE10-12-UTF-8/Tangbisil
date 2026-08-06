package com.back.domain.member.passwordReset.service

import com.back.domain.member.member.repository.MemberRepository
import com.back.domain.member.passwordReset.repository.PasswordResetTokenRepository
import com.back.global.exception.ServiceException
import com.back.global.util.EmailNormalizer
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PasswordResetService(
    private val tokenRepository: PasswordResetTokenRepository,
    private val tokenIssuer: PasswordResetTokenIssuer,
    private val emailSender: PasswordResetEmailSender,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder
) {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun sendResetCode(email: String) {
        val normalizedEmail = EmailNormalizer.normalize(email)
        val result = issueWithRetry(normalizedEmail)

        if (result.memberExists) {
            emailSender.sendAsync(normalizedEmail, result.code)
        }
    }

    private fun issueWithRetry(email: String): PasswordResetTokenIssuer.IssueResult {
        return try {
            tokenIssuer.issue(email)
        } catch (e: DataIntegrityViolationException) {
            // 완전히 동시에 들어온 첫 요청끼리 유니크 제약에 부딪힌 경우, 새 트랜잭션으로 한 번 재시도
            tokenIssuer.issue(email)
        }
    }

    @Transactional(noRollbackFor = [ServiceException::class])
    fun resetPassword(email: String, code: String, newPassword: String) {
        val normalizedEmail = EmailNormalizer.normalize(email)

        val token = tokenRepository.findTopByEmailOrderByCreatedAtDesc(normalizedEmail)
            ?: throw ServiceException("400-1", "발송된 재설정 코드가 없습니다.")

        if (token.isExpired()) {
            throw ServiceException("400-2", "재설정 코드가 만료되었습니다.")
        }
        if (token.isBlocked()) {
            throw ServiceException("400-5", "인증 시도 횟수를 초과했습니다. 코드를 다시 발송해주세요.")
        }
        if (!token.verifyCode(code)) {
            throw ServiceException("400-3", "재설정 코드가 일치하지 않습니다.")
        }

        val member = memberRepository.findByEmail(normalizedEmail)
            ?: throw ServiceException("404-1", "존재하지 않는 회원입니다.")

        if (passwordEncoder.matches(newPassword, member.password)) {
            throw ServiceException("400-4", "현재 비밀번호와 동일한 비밀번호로는 변경할 수 없습니다.")
        }

        member.updatePassword(passwordEncoder.encode(newPassword)!!)
        tokenRepository.deleteByEmail(normalizedEmail)
    }
}
