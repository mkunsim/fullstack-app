package com.example.safari.security;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(
    name = "auth_sessions",
    indexes = @Index(
        name = "idx_auth_sessions_token_hash",
        columnList = "token_hash",
        unique = true
    )
)
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Builder
    public AuthSession(String tokenHash, String email, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.email = email;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }
}