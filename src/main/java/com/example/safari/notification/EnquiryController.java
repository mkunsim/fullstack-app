package com.example.safari.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/enquiries")
public class EnquiryController {

    private final MailOutboxService outbox;
    private final String operatorEmail;
    private final String sender;

    public EnquiryController(
            MailOutboxService outbox,
            @Value("${app.tour-operator.email:}") String operatorEmail,
            @Value("${spring.mail.username:}") String sender
    ) {
        this.outbox = outbox;
        this.operatorEmail = operatorEmail;
        this.sender = sender;
    }

    @PostMapping
    public ResponseEntity<EnquiryResponse> submit(@Valid @RequestBody EnquiryRequest request) {
        if (operatorEmail == null || operatorEmail.isBlank() || sender == null || sender.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Enquiries are temporarily unavailable.");
        }

        String itemName = request.itemName().trim().replaceAll("[\\r\\n]+", " ");
        String subject = "Website enquiry | " + request.itemType() + " | " + itemName;
        String body = "A customer sent a website enquiry.\n\n"
                + "Name: " + request.name().trim() + "\n"
                + "Email: " + request.email().trim() + "\n"
                + "About: " + request.itemType() + " - " + itemName + "\n\n"
                + "Message:\n" + request.message().trim();

        outbox.enqueue(
                "enquiry:" + UUID.randomUUID(),
                operatorEmail.trim(),
                subject,
                body
        );
        return ResponseEntity.accepted()
                .body(new EnquiryResponse("Enquiry received. No booking has been created."));
    }

    public enum EnquiryType {
        TOUR,
        VEHICLE
    }

    public record EnquiryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull EnquiryType itemType,
            @NotBlank @Size(max = 160) String itemName,
            @NotBlank @Size(max = 3000) String message
    ) {}

    public record EnquiryResponse(String message) {}
}
