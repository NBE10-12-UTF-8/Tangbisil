package com.back.domain.member.emailVerification.service;

import com.back.domain.member.emailVerification.entity.EmailVerificationToken;
import com.back.domain.member.emailVerification.repository.EmailVerificationTokenRepository;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.global.email.ResendEmailService;
import com.back.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {
    private final EmailVerificationTokenRepository tokenRepository;
    private final MemberRepository memberRepository;
    private final ResendEmailService resendEmailService;

    @Value("${custom.email.verification.code-expiration-minutes}")
    private int expirationMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void sendVerificationCode(String email) {
        memberRepository.findByEmail(email).ifPresent(_ -> {
            throw new ServiceException("409-1", "이미 가입된 이메일입니다.");
        });

        tokenRepository.findTopByEmailOrderByCreatedAtDesc(email).ifPresent(prev -> {
            if (prev.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(60))) {
                throw new ServiceException("429-1", "잠시 후 다시 시도해주세요.");
            }
        });

        tokenRepository.deleteByEmail(email);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerificationToken token = new EmailVerificationToken(email, code, expirationMinutes);
        tokenRepository.save(token);

        String html = "<p>인증 코드: <b>" + code + "</b></p><p>" + expirationMinutes + "분 내에 입력해주세요.</p>";
        resendEmailService.send(email, "[탕비실] 이메일 인증 코드", html);
    }

    @Transactional
    public void confirmVerificationCode(String email, String code) {
        EmailVerificationToken token = tokenRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ServiceException("400-1", "발송된 인증 코드가 없습니다."));

        if (token.isExpired()) {
            throw new ServiceException("400-2", "인증 코드가 만료되었습니다.");
        }
        if (!token.matches(code)) {
            throw new ServiceException("400-3", "인증 코드가 일치하지 않습니다.");
        }

        token.markVerified();
        tokenRepository.save(token);
    }

    // 회원가입 시점에 호출: 인증된 이메일인지 확인 후 토큰 소모
    @Transactional
    public void consumeVerifiedEmail(String email) {
        EmailVerificationToken token = tokenRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ServiceException("400-4", "이메일 인증이 필요합니다."));

        if (!token.isVerified()) {
            throw new ServiceException("400-4", "이메일 인증이 필요합니다.");
        }

        tokenRepository.deleteByEmail(email);
    }
}