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
        PasswordResetTokenIssuer.IssueResult result = tokenIssuer.issue(normalizedEmail);

        if (!result.memberExists()) {
            // 토큰은 이미 만들어졌으니 재발송 제한은 가입된 이메일과 동일하게 동작한다.
            // 다만 실제 메일은 보내지 않는다.
            return;
        }

        String html = PasswordResetEmailTemplate.render(result.code(), expirationMinutes);
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

        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            throw new ServiceException("400-4", "현재 비밀번호와 동일한 비밀번호로는 변경할 수 없습니다.");
        }

        member.updatePassword(passwordEncoder.encode(newPassword));
        tokenRepository.deleteByEmail(normalizedEmail);
    }
}