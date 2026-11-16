package com.example.safari.payment;

import com.example.safari.booking.Booking;
import com.example.safari.booking.BookingRepository;
import com.example.safari.car.CarBooking;
import com.example.safari.car.CarBookingRepository;
import com.example.safari.security.PasswordlessAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentVerificationController {

    private static final Logger log = LoggerFactory.getLogger(PaymentVerificationController.class);

    private final PaymentProvider paymentProvider;
    private final PaymentCompletionService paymentCompletionService;
    private final BookingRepository bookingRepository;
    private final CarBookingRepository carBookingRepository;
    private final PasswordlessAuthService passwordlessAuthService;

    public PaymentVerificationController(
            PaymentProvider paymentProvider,
            PaymentCompletionService paymentCompletionService,
            BookingRepository bookingRepository,
            CarBookingRepository carBookingRepository,
            PasswordlessAuthService passwordlessAuthService
    ) {
        this.paymentProvider = paymentProvider;
        this.paymentCompletionService = paymentCompletionService;
        this.bookingRepository = bookingRepository;
        this.carBookingRepository = carBookingRepository;
        this.passwordlessAuthService = passwordlessAuthService;
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @RequestBody VerifyPaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        String customerEmail = passwordlessAuthService.customerEmailFromRequest(httpRequest);
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authentication required."
            );
        }
        if (request == null || request.reference() == null
                || request.reference().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Payment reference is required."
            );
        }
        final PaymentProvider.PaymentVerification verification;
        try {
            verification = paymentProvider.verifyTransaction(request.reference().trim());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Unable to verify payment with Paystack.", ex
            );
        }
        if (verification == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Payment is not associated with a booking."
            );
        }
        Long bookingId = verification.bookingId();
        if (bookingId == null || bookingId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Payment is not associated with a valid booking."
            );
        }
        if (verification.isCarBooking()) {
            CarBooking carBooking = carBookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Vehicle booking not found."
                    ));
            if (carBooking.getCustomer() == null
                    || carBooking.getCustomer().getEmail() == null
                    || !carBooking.getCustomer().getEmail().trim()
                            .equalsIgnoreCase(customerEmail.trim())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You do not own this vehicle booking."
                );
            }
            try {
                CarBooking updated = paymentCompletionService.completeCarVerified(
                        verification, request.reference().trim()
                );
                log.info("Vehicle payment verification completed for booking {}", updated.getId());
                return ResponseEntity.ok(Map.of(
                        "message", "Payment verified",
                        "carBookingId", updated.getId(),
                        "paymentStatus", updated.getPaymentStatus().name()
                ));
            } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
            }
        }
        long validBookingId = bookingId;
        Booking booking = bookingRepository.findById(validBookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found."
                ));
        if (booking.getCustomer() == null
                || booking.getCustomer().getEmail() == null
                || !booking.getCustomer().getEmail().trim()
                        .equalsIgnoreCase(customerEmail.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You do not own this booking."
            );
        }
        try {
            Booking updated = paymentCompletionService.completeVerified(
                    verification, request.reference().trim()
            );
            log.info("Payment verification completed for booking {}", updated.getId());
            return ResponseEntity.ok(Map.of(
                    "message", "Payment verified",
                    "bookingId", updated.getId(),
                    "paymentStatus", updated.getPaymentStatus().name()
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    public record VerifyPaymentRequest(String reference) {}
}
