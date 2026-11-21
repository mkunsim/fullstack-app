package com.example.safari.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.safari.notification.MailOutboxService;

@Service
public class BookingEmailService {
    private static final Logger log = LoggerFactory.getLogger(BookingEmailService.class);
    private final MailOutboxService outbox;
    @Value("${spring.mail.username:}") private String sender;

    public BookingEmailService(MailOutboxService outbox) { this.outbox = outbox; }

    public void sendConfirmation(Booking booking) {
        if (sender == null || sender.isBlank()) {
            log.info("Email not configured; skipping confirmation for booking {}", booking.getId());
            return;
        }
        String subject = "Wildpath Safaris | Booking Request Received #" + booking.getId();
        String body = "Hello " + booking.getCustomer().getFullName() + ",\n\n"
            + "Thank you for choosing Wildpath Safaris for your upcoming adventure!\n\n"
            + "We've successfully received your safari booking request. Here are your booking details:\n\n"
            + "BOOKING DETAILS\n"
            + "------------------------------\n"
            + "Booking Reference: #" + booking.getId() + "\n"
            + "Safari: " + booking.getDeparture().getTour().getName() + "\n"
            + "Travel Date: " + booking.getDeparture().getStartDate() + "\n"
            + "Number of Guests: " + booking.getGuests() + "\n"
            + optionalExperiencesSummary(booking)
            + "Total: " + booking.getCurrency() + " " + booking.getTotalAmount() + "\n"
            + "Booking Status: " + booking.getStatus() + "\n\n"
            + "WHAT HAPPENS NEXT?\n"
            + "Our team will review your request and follow up regarding availability "
            + "and the next steps to confirm your safari.\n\n"
            + "Please note that your booking is currently a request and is not yet "
            + "a confirmed reservation. This email is not a payment confirmation or receipt.\n\n"
            + "If you have any questions or need to update your booking details, "
            + "simply reply to this email and we'll be happy to assist.\n\n"
            + "Thank you for trusting Wildpath Safaris. We look forward to helping "
            + "you create an unforgettable African safari experience!\n\n"
            + "Warm regards,\n"
            + "The Wildpath Safaris Team\n"
            + "Your journey into the wild begins here.";
        outbox.enqueue("booking:" + booking.getId() + ":request-customer",
                booking.getCustomer().getEmail(), subject, body);
    }

    private String optionalExperiencesSummary(Booking booking) {

        if (booking.getOptionalExperiences() == null || booking.getOptionalExperiences().isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder("Optional optional-experiences:\n");

        booking.getOptionalExperiences().forEach(experience -> summary
                .append("- ")
                .append(experience.getName())
                .append(" (USD ")
                .append(experience.getPrice())
                .append(" per booking)\n")
        );

        return summary.append('\n').toString();
    }

    public void sendStatusUpdate(Booking booking) {
        if (sender == null || sender.isBlank()) {
            log.info("Email not configured; skipping status update for booking {}", booking.getId());
            return;
        }
        String subject = "Wildpath Safaris | Booking #" + booking.getId() + " Update";
        String body = "Hello " + booking.getCustomer().getFullName() + ",\n\n"
                + "Your safari booking request for " + booking.getDeparture().getTour().getName()
                + " has been updated to " + booking.getStatus() + ".\n\n"
                + "Please contact Wildpath Safaris if you have questions.";
        outbox.enqueue("booking:" + booking.getId() + ":status:" + booking.getStatus(),
                booking.getCustomer().getEmail(), subject, body);
    }

    public void sendExpiryReminder(Booking booking) {
        if (sender == null || sender.isBlank()) return;
        outbox.enqueue(
                "booking:" + booking.getId() + ":expiry-reminder",
                booking.getCustomer().getEmail(),
                "Wildpath Safaris | Booking request #" + booking.getId() + " expires soon",
                "Hello " + booking.getCustomer().getFullName() + ",\n\n"
                        + "Your request for " + booking.getDeparture().getTour().getName()
                        + " is still awaiting confirmation. It will expire soon if it is not processed."
        );
    }

    public void sendExpiredNotice(Booking booking) {
        if (sender == null || sender.isBlank()) return;
        outbox.enqueue(
                "booking:" + booking.getId() + ":expired",
                booking.getCustomer().getEmail(),
                "Wildpath Safaris | Booking request #" + booking.getId() + " expired",
                "Hello " + booking.getCustomer().getFullName() + ",\n\n"
                        + "Your unconfirmed request for " + booking.getDeparture().getTour().getName()
                        + " has expired. Please submit a new request if you are still interested."
        );
    }
}
