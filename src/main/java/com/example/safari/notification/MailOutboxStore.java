package com.example.safari.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@Service
public class MailOutboxStore {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxStore.class);

    private final MailOutboxRepository messages;

    public MailOutboxStore(MailOutboxRepository messages) {
        this.messages = messages;
    }

    @Transactional
    public List<MailOutboxMessage> claimBatch() {
        Instant now = Instant.now();
        messages.releaseExpiredClaims(MailOutboxStatus.PENDING, MailOutboxStatus.SENDING, now);
        List<MailOutboxMessage> due = messages
                .findTop20ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                        MailOutboxStatus.PENDING, now);
        due.forEach(message -> message.claim(now.plusSeconds(300)));
        return due;
    }

    @Transactional
    public void markSent(Long id) {
        messages.findById(id).ifPresent(message -> {
            message.markSent(Instant.now());
            messages.save(message);
        });
    }

    @Transactional
    public void scheduleRetry(Long id, int attempts, RuntimeException failure) {
        messages.findById(id).ifPresent(message -> {
            long delaySeconds = Math.min(3600, 30L << Math.min(attempts, 7));
            message.scheduleRetry(
                    Instant.now().plusSeconds(delaySeconds),
                    failure.getClass().getSimpleName(),
                    attempts >= 8
            );
            messages.save(message);
            if (attempts >= 8) {
                log.error("Mail outbox message {} exhausted retries after {} attempts",
                        id, attempts);
            }
        });
    }
}
