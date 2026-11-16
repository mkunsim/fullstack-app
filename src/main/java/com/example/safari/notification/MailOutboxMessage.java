package com.example.safari.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "mail_outbox", indexes = @Index(
        name = "idx_mail_outbox_due",
        columnList = "status,next_attempt_at"
))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 255)
    private String dedupeKey;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MailOutboxStatus status = MailOutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public MailOutboxMessage(String dedupeKey, String recipient, String subject, String body) {
        this.dedupeKey = dedupeKey;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    public void claim(Instant leaseUntil) {
        status = MailOutboxStatus.SENDING;
        attempts++;
        nextAttemptAt = leaseUntil;
    }

    public void markSent(Instant sentAt) {
        status = MailOutboxStatus.SENT;
        this.sentAt = sentAt;
        lastError = null;
    }

    public void scheduleRetry(Instant nextAttemptAt, String error, boolean exhausted) {
        status = exhausted ? MailOutboxStatus.FAILED : MailOutboxStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        lastError = error;
    }
}
