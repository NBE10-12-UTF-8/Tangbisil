package com.back.domain.member.passwordReset.service;

import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.passwordReset.entity.PasswordResetToken;
import com.back.domain.member.passwordReset.repository.PasswordResetTokenRepository;
import com.back.global.email.PasswordResetEmailTemplate;
import com.back.global.email.ResendEmailService;
import com.back.global.exception.ServiceException;
import com.back.global.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetTokenIssuer tokenIssuer;
    private final MemberRepository memberRepository;
    private final ResendEmailService resendEmailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${custom.password-reset.code-expiration-minutes}")
    private int expirationMinutes;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendResetCode(String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        String code = tokenIssuer.issueIfMemberExists(normalizedEmail);

        if (code == null) {
            // 가입되지 않은 이메일이어도 동일하게 성공 처리 (이메일 존재 여부 노출 방지)
            return;
        }

        String html = PasswordResetEmailTemplate.render(code, expirationMinutes);
        try {
            resendEmailService.send(normalizedEmail, "[탕비실] 비밀번호 재설정 코드", html);
        } catch (RuntimeException e) {
            tokenIssuer.deleteToken(normalizedEmail);
            throw e;
        }
    }

    @Transactional(noRollbackFor = ServiceException.class)
    public void resetPassword(String email, String code, String newPassword) {
        String normalizedEmail = EmailNormalizer.normalize(email);

        PasswordResetToken token = tokenRepository.findTopByEmailOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> new ServiceException("400-1", "발송된 재설정 코드가 없습니다."));

        if (token.isExpired()) {
            throw new ServiceException("400-2", "재설정 코드가 만료되었습니다.");
        }
        if (token.isBlocked()) {
            throw new ServiceException("400-5", "인증 시도 횟수를 초과했습니다. 코드를 다시 발송해주세요.");
        }
        if (!token.matches(code)) {
            throw new ServiceException("400-3", "재설정 코드가 일치하지 않습니다.");
        }

        Member member = memberRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ServiceException("404-1", "존재하지 않는 회원입니다."));

        member.updatePassword(passwordEncoder.encode(newPassword));
        tokenRepository.deleteByEmail(normalizedEmail);
    }
}