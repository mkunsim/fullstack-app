
package com.example.safari.operator;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.safari.booking.Booking;
import com.example.safari.booking.BookingRepository;
import com.example.safari.notification.MailOutboxService;

@Service
public class OperatorEmailService {

    private static final Logger log =
            LoggerFactory.getLogger(OperatorEmailService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MailOutboxService outbox;
    private final String sender;
    private final String operatorEmail;
    private final String operatorName;
    private final String frontendUrl;
    private final BookingRepository bookingRepository;
    public OperatorEmailService(
            MailOutboxService outbox,
            @Value("${spring.mail.username:}") String sender,
            @Value("${app.tour-operator.email:}") String operatorEmail,
            @Value("${app.tour-operator.name:Simon M. Mwanza}") String operatorName,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            BookingRepository bookingRepository) {

        this.outbox = outbox;
        this.sender = sender;
        this.operatorEmail = operatorEmail;
        this.operatorName = operatorName;
        this.frontendUrl = frontendUrl;
        this.bookingRepository = bookingRepository;
    }

    public void requestAvailability(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }

        if (sender.isBlank() || operatorEmail.isBlank()) {
            log.warn("Sender or operator email is not configured.");
            return;
        }

        var tour = booking.getDeparture().getTour();
        String actionToken = generateActionToken();
        booking.setActionToken(actionToken);
        booking.setActionTokenExpiresAt(
                java.time.Instant.now().plus(java.time.Duration.ofDays(3)));
        bookingRepository.save(booking);

        String actionPage = frontendUrl
                + "/operator/booking-action?token=" + actionToken;
        String body =
                    "Dear " + operatorName + ",\n\n"
                    + "Please check availability for this booking.\n\n"
                    + "Booking reference: #" + booking.getId() + "\n"
                    + "Tour: " + tour.getName() + "\n"
                    + "Travel date: "
                    + booking.getDeparture().getStartDate() + "\n"
                    + "Guests: " + booking.getGuests() + "\n"
                    + "Customer: "
                    + booking.getCustomer().getFullName() + "\n"
                    + "Customer email: "
                    + booking.getCustomer().getEmail() + "\n\n"
                    + optionalExperiencesSummary(booking)
                    + "Booking total: " + booking.getCurrency() + " "
                    + booking.getTotalAmount() + "\n\n"
                    + "Please use the following links to respond:\n\n"
                    + "Confirm availability: " + actionPage
                    + "&action=confirm\n"
                    + "Decline request: " + actionPage
                    + "&action=decline\n\n"
                    + "Please confirm availability and advise us of any "
                    + "pricing changes or booking conditions.\n\n"
                    + "Kind regards,\n"
                    + "Wildpath Safaris Booking Team";
        outbox.enqueue(
                "booking:" + booking.getId() + ":operator-request",
                operatorEmail,
                "Safari Availability Request - Booking #" + booking.getId(),
                body
        );
        log.info("Operator availability notification queued for booking {}", booking.getId());
    }

    private String optionalExperiencesSummary(Booking booking) {
        if (booking.getOptionalExperiences().isEmpty()) return "";
        StringBuilder summary = new StringBuilder("Optional special experiences:\n");
        booking.getOptionalExperiences().forEach(experience -> summary
                .append("- ")
                .append(experience.getName())
                .append(" (USD ")
                .append(experience.getPrice())
                .append(" per booking)\n"));
        return summary.append('\n').toString();
    }

    private String generateActionToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
