package com.example.safari.security;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(
        name = "app_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_app_users_email",
                columnNames = "email"
        )
)
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Custom constructor with normalisation logic
    public AppUser(String email, String passwordHash, String fullName, Role role) {
        this.email = email != null ? email.toLowerCase().trim() : null;
        this.passwordHash = passwordHash;
        this.fullName = fullName != null ? fullName.trim() : null;
        this.role = role;
        this.enabled = true;
        this.createdAt = Instant.now();
    }
}