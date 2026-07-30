package com.back.domain.member.passwordReset.service;

import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.passwordReset.entity.PasswordResetToken;
import com.back.domain.member.passwordReset.repository.PasswordResetTokenRepository;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class PasswordResetTokenIssuer {

    private final PasswordResetTokenRepository tokenRepository;
    private final MemberRepository memberRepository;

    @Value("${custom.password-reset.code-expiration-minutes}")
    private int expirationMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    record IssueResult(String code, boolean memberExists) {}

    // 회원이 존재할 때만 토큰을 발급하고 코드를 반환한다.
    // 존재하지 않으면 null을 반환할 뿐, 예외를 던지지 않는다 (이메일 존재 여부 노출 방지).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IssueResult issue(String email) {
        tokenRepository.findTopByEmailOrderByCreatedAtDesc(email).ifPresent(prev -> {
            if (prev.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(60))) {
                throw new ServiceException("429-1", "잠시 후 다시 시도해주세요.");
            }
        });

        tokenRepository.deleteByEmail(email);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        tokenRepository.save(new PasswordResetToken(email, code, expirationMinutes));

        boolean memberExists = memberRepository.findByEmail(email).isPresent();
        return new IssueResult(code, memberExists);
    }

    @Transactional
    public void deleteToken(String email) {
        tokenRepository.deleteByEmail(email);
    }
}