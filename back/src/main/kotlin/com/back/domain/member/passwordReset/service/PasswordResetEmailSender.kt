package com.back.domain.member.passwordReset.service

import com.back.global.email.PasswordResetEmailTemplate
import com.back.global.email.ResendEmailService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PasswordResetEmailSender(
    private val resendEmailService: ResendEmailService,
    private val tokenIssuer: PasswordResetTokenIssuer
) {
    private val log = LoggerFactory.getLogger(PasswordResetEmailSender::class.java)

    @Value("\${custom.password-reset.code-expiration-minutes}")
    private var expirationMinutes: Int = 0

    @Async
    fun sendAsync(email: String, code: String) {
        val html = PasswordResetEmailTemplate.render(code, expirationMinutes)
        try {
            resendEmailService.send(email, "[탕비실] 비밀번호 재설정 코드", html)
        } catch (e: Exception) {
            log.error("비밀번호 재설정 메일 발송 실패: email={}", email, e)
            tokenIssuer.deleteToken(email)
        }
    }
}
