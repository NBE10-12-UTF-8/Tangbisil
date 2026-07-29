package com.back.domain.member.emailVerification.service;

import com.back.domain.member.emailVerification.entity.EmailVerificationToken;
import com.back.domain.member.emailVerification.repository.EmailVerificationTokenRepository;
import com.back.global.email.ResendEmailService;
import com.back.global.exception.ServiceException;
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

    // DB 트랜잭션(tokenIssuer.issue)이 커밋된 뒤 트랜잭션 밖에서 외부 API 호출
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendVerificationCode(String email) {
        String code = tokenIssuer.issue(email);

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

    // 회원가입 시점에 호출: 인증 여부 + 만료 여부 확인 후 토큰 소모
    @Transactional
    public void consumeVerifiedEmail(String email) {
        EmailVerificationToken token = tokenRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ServiceException("400-4", "이메일 인증이 필요합니다."));

        if (!token.isVerified() || token.isExpired()) {
            throw new ServiceException("400-4", "이메일 인증이 필요하거나 만료되었습니다.");
        }

        tokenRepository.deleteByEmail(email);
    }
}