package com.example.safari.car;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.safari.notification.MailOutboxService;

@Service
public class CarBookingEmailService {
    private static final Logger log = LoggerFactory.getLogger(CarBookingEmailService.class);

    private final MailOutboxService outbox;
    private final String sender;
    private final String operatorEmail;

    public CarBookingEmailService(
            MailOutboxService outbox,
            @Value("${spring.mail.username:}") String sender,
            @Value("${app.tour-operator.email:}") String operatorEmail
    ) {
        this.outbox = outbox;
        this.sender = sender;
        this.operatorEmail = operatorEmail;
    }

    public void sendRequestReceived(CarBooking booking) {
        enqueue(booking, "request-customer", booking.getCustomer().getEmail(),
                "Wildpath Safaris | Vehicle Request #" + booking.getId(),
                "Hello " + booking.getCustomer().getFullName() + ",\n\n"
                        + "We received your vehicle request for " + booking.getCar().getMake() + ".\n"
                        + "Dates: " + booking.getPickupDate() + " to " + booking.getDropoffDate() + "\n"
                        + "Route: " + booking.getPickupLocation() + " to " + booking.getDropoffLocation() + "\n"
                        + "Estimated total: " + booking.getCurrency() + " " + booking.getTotalAmount() + "\n"
                        + "Status: " + booking.getStatus() + ". The operator will review availability.");

        if (operatorEmail != null && !operatorEmail.isBlank()) {
            enqueue(booking, "operator-request", operatorEmail,
                    "Vehicle Availability Request #" + booking.getId(),
                    "A vehicle request needs review.\n"
                            + "Vehicle: " + booking.getCar().getMake() + "\n"
                            + "Customer: " + booking.getCustomer().getFullName() + " ("
                            + booking.getCustomer().getEmail() + ")\n"
                            + "Dates: " + booking.getPickupDate() + " to " + booking.getDropoffDate() + "\n"
                            + "Route: " + booking.getPickupLocation() + " to " + booking.getDropoffLocation());
        }
    }

    public void sendStatusUpdate(CarBooking booking) {
        enqueue(booking, "status:" + booking.getStatus(), booking.getCustomer().getEmail(),
                "Wildpath Safaris | Vehicle Request #" + booking.getId() + " Update",
                "Hello " + booking.getCustomer().getFullName() + ",\n\n"
                        + "Your vehicle request for " + booking.getCar().getMake()
                        + " has been updated to " + booking.getStatus() + ".");
    }

    public void sendExpiryReminder(CarBooking booking) {
        enqueue(booking, "expiry-reminder", booking.getCustomer().getEmail(),
                "Wildpath Safaris | Vehicle request #" + booking.getId() + " expires soon",
                "Hello " + booking.getCustomer().getFullName() + ",\n\n"
                        + "Your vehicle request for " + booking.getCar().getMake()
                        + " is still awaiting confirmation and will expire soon.");
    }

    public void sendExpiredNotice(CarBooking booking) {
        enqueue(booking, "expired", booking.getCustomer().getEmail(),
                "Wildpath Safaris | Vehicle request #" + booking.getId() + " expired",
                "Hello " + booking.getCustomer().getFullName() + ",\n\n"
                        + "Your unconfirmed vehicle request for " + booking.getCar().getMake()
                        + " has expired. Please submit a new request if you are still interested.");
    }

    private void enqueue(CarBooking booking, String event, String recipient, String subject, String body) {
        if (sender == null || sender.isBlank() || recipient == null || recipient.isBlank()) {
            log.info("Email is not configured; skipping vehicle booking notification.");
            return;
        }
        outbox.enqueue("car-booking:" + booking.getId() + ":" + event, recipient, subject, body);
    }
}
