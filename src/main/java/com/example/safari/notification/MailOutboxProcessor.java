package com.example.safari.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MailOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxProcessor.class);
    private final MailOutboxStore outbox;
    private final JavaMailSender mailSender;
    private final String sender;

    public MailOutboxProcessor(
            MailOutboxStore outbox,
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String sender
    ) {
        this.outbox = outbox;
        this.mailSender = mailSender;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${app.mail-outbox.poll-ms:5000}")
    public void deliverDueMessages() {
        if (sender == null || sender.isBlank()) {
            return;
        }
        for (MailOutboxMessage message : outbox.claimBatch()) {
            Long id = message.getId();
            if (id == null) {
                log.error("Mail outbox message has no ID; skipping delivery");
                continue;
            }
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom(sender);
                mail.setTo(message.getRecipient());
                mail.setSubject(message.getSubject());
                mail.setText(message.getBody());
                mailSender.send(mail);
                outbox.markSent(id);
            } catch (RuntimeException ex) {
                outbox.scheduleRetry(id, message.getAttempts(), ex);
                log.error("Mail outbox message {} delivery failed", message.getId(), ex);
            }
        }
    }
}
