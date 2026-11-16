
package com.example.safari.operator;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.safari.booking.Booking;
import com.example.safari.booking.BookingRepository;
import com.example.safari.booking.BookingService;
import com.example.safari.booking.BookingStatus;
import com.example.safari.config.PricingConversionService;

@RestController
@RequestMapping("/api/operator")
public class OperatorBookingController {

    private static final Logger log =
            LoggerFactory.getLogger(OperatorBookingController.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PricingConversionService pricingConversionService;

    public OperatorBookingController(
            BookingRepository bookingRepository,
            BookingService bookingService,
            PricingConversionService pricingConversionService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.pricingConversionService = pricingConversionService;
    }

    @GetMapping("/booking-action/details")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getBookingActionDetails(
            @RequestParam(required = false) String token) {

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Missing booking action token.");
        }

        Booking booking = bookingRepository
                .findByActionToken(token)
                .orElse(null);

        if (booking == null) {
            return ResponseEntity.badRequest()
                    .body("This link is invalid or has expired.");
        }

        Instant expiresAt = booking.getActionTokenExpiresAt();

        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            return ResponseEntity.badRequest()
                    .body("This link is invalid or has expired.");
        }

        if (booking.getDeparture() == null
                || booking.getDeparture().getTour() == null
                || booking.getCustomer() == null) {

            log.error(
                    "Booking {} has missing departure, tour, or customer data.",
                    booking.getId()
            );

            return ResponseEntity.internalServerError()
                    .body("Booking details are incomplete.");
        }

        var departure = booking.getDeparture();
        var tour = departure.getTour();
        var customer = booking.getCustomer();

        BookingActionDetails details = new BookingActionDetails(
                booking.getId(),
                tour.getName(),
                tour.getDestination(),
                departure.getStartDate() == null
                        ? null : departure.getStartDate().toString(),
                tour.getDurationDays(),
                booking.getGuests(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getStatus().toString(),
                booking.getPaymentStatus() == null
                        ? "UNKNOWN" : booking.getPaymentStatus().toString(),
                customer.getFullName(),
                customer.getEmail(),
                booking.getCreatedAt() == null
                        ? null : booking.getCreatedAt().toString()
        );

        return ResponseEntity.ok(details);
    }

    public record BookingActionDetails(
            Long bookingId,
            String tourName,
            String destination,
            String departureDate,
            int durationDays,
            int guests,
            Object totalAmount,
            String currency,
            String status,
            String paymentStatus,
            String customerName,
            String customerEmail,
            String requestedAt
    ) {}

    @PostMapping("/booking-action")
    @Transactional
    public ResponseEntity<String> handleAction(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String action) {

        log.info(
                "Operator booking action received. Action={}, tokenPresent={}",
                action,
                token != null && !token.isBlank()
        );

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Missing booking action token.");
        }

        if (action == null
                || (!"confirm".equalsIgnoreCase(action)
                && !"decline".equalsIgnoreCase(action))) {

            return ResponseEntity.badRequest()
                    .body("Invalid action. Use confirm or decline.");
        }

        Booking booking = bookingRepository
                .findByActionTokenForUpdate(token)
                .orElse(null);

        if (booking == null) {
            return ResponseEntity.badRequest()
                    .body("This link is invalid or has expired.");
        }

        Instant expiresAt = booking.getActionTokenExpiresAt();

        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            return ResponseEntity.badRequest()
                    .body("This link is invalid or has expired.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            return ResponseEntity.badRequest()
                    .body("This booking has already been processed.");
        }

        BookingStatus finalStatus;
        if ("confirm".equalsIgnoreCase(action)) {
            pricingConversionService.convertBookingToKes(booking);
            finalStatus = BookingStatus.CONFIRMED;
        } else {
            finalStatus = BookingStatus.DECLINED;
        }

        Booking savedBooking = bookingService.updatePendingStatus(booking.getId(), finalStatus);

        log.info(
                "Booking {} updated to {}",
                savedBooking.getId(),
                savedBooking.getStatus()
        );

        return ResponseEntity.ok(
                "Booking #" + savedBooking.getId()
                        + " updated to " + savedBooking.getStatus() + "."
        );
    }

    @PostMapping("/bookings/{id}/confirm")
    public ResponseEntity<String> confirmBooking(@PathVariable Long id) {

        bookingService.confirmBooking(id);

        return ResponseEntity.ok("Booking #" + id + " confirmed.");
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<String> cancelBooking(@PathVariable Long id) {

        bookingService.cancelBooking(id);

        return ResponseEntity.ok("Booking #" + id + " cancelled.");
    }
}
