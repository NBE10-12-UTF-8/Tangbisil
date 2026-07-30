package com.back.domain.member.passwordReset.service;

import com.back.global.email.PasswordResetEmailTemplate;
import com.back.global.email.ResendEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetEmailSender {

    private final ResendEmailService resendEmailService;
    private final PasswordResetTokenIssuer tokenIssuer;

    @Value("${custom.password-reset.code-expiration-minutes}")
    private int expirationMinutes;

    @Async
    public void sendAsync(String email, String code) {
        String html = PasswordResetEmailTemplate.render(code, expirationMinutes);
        try {
            resendEmailService.send(email, "[탕비실] 비밀번호 재설정 코드", html);
        } catch (Exception e) {
            log.error("비밀번호 재설정 메일 발송 실패: email={}", email, e);
            tokenIssuer.deleteToken(email);
        }
    }
}