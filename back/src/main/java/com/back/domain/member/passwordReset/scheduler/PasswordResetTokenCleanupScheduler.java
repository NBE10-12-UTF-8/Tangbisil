package com.back.domain.member.passwordReset.scheduler;

import com.back.domain.member.passwordReset.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetTokenCleanupScheduler {

    private final PasswordResetTokenRepository tokenRepository;

    @Scheduled(cron = "0 0 * * * *") // 매시 정각
    @Transactional
    public void cleanupExpiredTokens() {
        tokenRepository.deleteAllExpiredBefore(LocalDateTime.now());
        log.info("[PasswordResetTokenCleanupScheduler] 만료된 비밀번호 재설정 토큰 정리 완료");
    }
}