package com.example.safari.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "login_otps")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginOtp {

    @Id
    @Column(length = 254)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(nullable = false)
    private int attempts;

    public LoginOtp(String email) {
        this.email = email;
    }

    public void issue(String codeHash, Instant expiresAt, Instant sentAt) {
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.lastSentAt = sentAt;
        this.attempts = 0;
    }

    public void recordFailedAttempt() {
        attempts++;
    }
}
