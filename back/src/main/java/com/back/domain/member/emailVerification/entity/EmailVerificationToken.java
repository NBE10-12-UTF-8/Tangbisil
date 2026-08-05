package com.back.domain.member.emailVerification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "email_verification_token")
public class EmailVerificationToken {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String code;
    private boolean verified;
    private int failedAttempts;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public EmailVerificationToken(String email, String code, int expirationMinutes) {
        this.email = email;
        this.code = code;
        this.expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isBlocked() {
        return failedAttempts >= MAX_ATTEMPTS;
    }

    // 코드가 틀리면 실패 횟수를 증가시킨다. 호출부에서 isBlocked()를 먼저 확인해야 한다.
    public boolean matches(String inputCode) {
        boolean matched = this.code.equals(inputCode);
        if (!matched) {
            this.failedAttempts++;
        }
        return matched;
    }

    public void markVerified() {
        this.verified = true;
    }
}