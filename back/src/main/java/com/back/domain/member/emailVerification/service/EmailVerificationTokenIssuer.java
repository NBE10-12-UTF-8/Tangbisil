package com.back.domain.member.emailVerification.service;

import com.back.domain.member.emailVerification.entity.EmailVerificationToken;
import com.back.domain.member.emailVerification.repository.EmailVerificationTokenRepository;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.global.exception.ServiceException;
import com.back.global.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class EmailVerificationTokenIssuer {

    private final EmailVerificationTokenRepository tokenRepository;
    private final MemberRepository memberRepository;

    @Value("${custom.email.verification.code-expiration-minutes}")
    private int expirationMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    // 순수 DB 트랜잭션. 외부 API 호출은 포함하지 않는다.
    @Transactional
    String issue(String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);

        memberRepository.findByEmail(normalizedEmail).ifPresent(_ -> {
            throw new ServiceException("409-1", "이미 가입된 이메일입니다.");
        });

        tokenRepository.findTopByEmailOrderByCreatedAtDesc(normalizedEmail).ifPresent(prev -> {
            if (prev.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(60))) {
                throw new ServiceException("429-1", "잠시 후 다시 시도해주세요.");
            }
        });

        tokenRepository.deleteByEmail(normalizedEmail);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        tokenRepository.save(new EmailVerificationToken(normalizedEmail, code, expirationMinutes));

        return code;
    }

    @Transactional
    void deleteToken(String email) {
        tokenRepository.deleteByEmail(email);
    }
}