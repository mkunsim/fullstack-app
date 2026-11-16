package com.example.safari.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailOutboxService {

    private final MailOutboxRepository messages;

    public MailOutboxService(MailOutboxRepository messages) {
        this.messages = messages;
    }

    @Transactional
    public void enqueue(String dedupeKey, String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        if (!messages.existsByDedupeKey(dedupeKey)) {
            messages.save(new MailOutboxMessage(dedupeKey, recipient, subject, body));
        }
    }
}
