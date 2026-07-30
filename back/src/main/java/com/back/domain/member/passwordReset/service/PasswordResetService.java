package com.back.domain.member.passwordReset.service;

import com.back.domain.member.member.entity.Member;
import com.back.domain.member.member.repository.MemberRepository;
import com.back.domain.member.passwordReset.entity.PasswordResetToken;
import com.back.domain.member.passwordReset.repository.PasswordResetTokenRepository;
import com.back.global.exception.ServiceException;
import com.back.global.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
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
    private final PasswordResetEmailSender emailSender;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendResetCode(String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        PasswordResetTokenIssuer.IssueResult result = tokenIssuer.issue(normalizedEmail);

        if (result.memberExists()) {
            emailSender.sendAsync(normalizedEmail, result.code());
        }
        // 회원 존재 여부와 무관하게 항상 즉시 반환한다.
        // 실제 메일 발송은 비동기로 처리해 응답 시간으로 이메일 존재 여부가 드러나지 않도록 한다.
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