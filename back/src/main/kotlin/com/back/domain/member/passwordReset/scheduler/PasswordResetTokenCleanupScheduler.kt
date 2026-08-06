package com.back.domain.member.passwordReset.scheduler

import com.back.domain.member.passwordReset.repository.PasswordResetTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class PasswordResetTokenCleanupScheduler(
    private val tokenRepository: PasswordResetTokenRepository
) {
    private val log = LoggerFactory.getLogger(PasswordResetTokenCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 * * * *") // 매시 정각
    @Transactional
    fun cleanupExpiredTokens() {
        tokenRepository.deleteAllExpiredBefore(LocalDateTime.now())
        log.info("[PasswordResetTokenCleanupScheduler] 만료된 비밀번호 재설정 토큰 정리 완료")
    }
}
