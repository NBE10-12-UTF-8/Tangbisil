package com.back.domain.member.emailVerification.service;

import com.back.domain.member.emailVerification.entity.EmailVerificationToken;
import com.back.domain.member.emailVerification.repository.EmailVerificationTokenRepository;
import com.back.global.email.ResendEmailService;
import com.back.global.email.VerificationEmailTemplate;
import com.back.global.exception.ServiceException;
import com.back.global.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationTokenIssuer tokenIssuer;
    private final ResendEmailService resendEmailService;

    @Value("${custom.email.verification.code-expiration-minutes}")
    private int expirationMinutes;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendVerificationCode(String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        String code = tokenIssuer.issue(normalizedEmail);

        String html = VerificationEmailTemplate.render(code, expirationMinutes);
        try {
            resendEmailService.send(normalizedEmail, "[탕비실] 이메일 인증 코드", html);
        } catch (RuntimeException e) {
            tokenIssuer.deleteToken(normalizedEmail);
            throw e;
        }
    }

    // failedAttempts 증가가 예외로 인해 롤백되지 않도록 noRollbackFor 지정
    @Transactional(noRollbackFor = ServiceException.class)
    public void confirmVerificationCode(String email, String code) {
        String normalizedEmail = EmailNormalizer.normalize(email);

        EmailVerificationToken token = tokenRepository.findTopByEmailOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> new ServiceException("400-1", "발송된 인증 코드가 없습니다."));

        if (token.isExpired()) {
            throw new ServiceException("400-2", "인증 코드가 만료되었습니다.");
        }
        if (token.isBlocked()) {
            throw new ServiceException("400-5", "인증 시도 횟수를 초과했습니다. 인증 코드를 다시 발송해주세요.");
        }
        if (!token.matches(code)) {
            throw new ServiceException("400-3", "인증 코드가 일치하지 않습니다.");
        }

        token.markVerified();
    }

    @Transactional
    public void consumeVerifiedEmail(String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);

        EmailVerificationToken token = tokenRepository.findTopByEmailOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> new ServiceException("400-4", "이메일 인증이 필요합니다."));

        if (!token.isVerified() || token.isExpired()) {
            throw new ServiceException("400-4", "이메일 인증이 필요하거나 만료되었습니다.");
        }

        tokenRepository.deleteByEmail(normalizedEmail);
    }
}