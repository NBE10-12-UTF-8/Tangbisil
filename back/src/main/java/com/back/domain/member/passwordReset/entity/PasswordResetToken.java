package com.back.domain.member.passwordReset.entity;

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
@Table(name = "password_reset_token")
public class PasswordResetToken {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String code;
    private int failedAttempts;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public PasswordResetToken(String email, String code, int expirationMinutes) {
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

    public boolean verifyCode(String inputCode) {
        boolean matched = this.code.equals(inputCode);
        if (!matched) {
            this.failedAttempts++;
        }
        return matched;
    }
}