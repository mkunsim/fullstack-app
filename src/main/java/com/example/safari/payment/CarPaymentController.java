package com.example.safari.payment;

import com.example.safari.car.CarBooking;
import com.example.safari.car.CarBookingRepository;
import com.example.safari.car.CarBookingStatus;
import com.example.safari.config.PricingConversionService;
import com.example.safari.security.PasswordlessAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments/car-bookings")
public class CarPaymentController {

    private static final String PAYMENT_CURRENCY = "KES";
    private static final long QUOTE_VALIDITY_SECONDS = 3600;

    private final CarBookingRepository bookings;
    private final PaymentProvider paymentProvider;
    private final PaymentCompletionService completionService;
    private final PasswordlessAuthService authService;
    private final PricingConversionService conversionService;

    public CarPaymentController(
            CarBookingRepository bookings,
            PaymentProvider paymentProvider,
            PaymentCompletionService completionService,
            PasswordlessAuthService authService,
            PricingConversionService conversionService
    ) {
        this.bookings = bookings;
        this.paymentProvider = paymentProvider;
        this.completionService = completionService;
        this.authService = authService;
        this.conversionService = conversionService;
    }

    @PostMapping("/{bookingId}/checkout")
    @Transactional
    public ResponseEntity<PaymentController.CheckoutResponse> checkout(
            @PathVariable long bookingId,
            HttpServletRequest request
    ) {
        final String customerEmail;
        try {
            customerEmail = authService.customerEmailFromRequest(request);
        } catch (ResponseStatusException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in before paying.");
        }

        CarBooking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle booking not found."));
        if (booking.getCustomer() == null || booking.getCustomer().getEmail() == null
                || !booking.getCustomer().getEmail().equalsIgnoreCase(customerEmail)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle booking not found.");
        }
        if (!CarBookingStatus.CONFIRMED.name().equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle booking must be confirmed before payment.");
        }

        PaymentStatus status = booking.getPaymentStatus();
        if (status == PaymentStatus.PAID) {
            return ResponseEntity.ok(response(booking, null, PaymentStatus.PAID));
        }
        if (status == null || status == PaymentStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle booking is not eligible for payment.");
        }

        if (status == PaymentStatus.INITIATED) {
            Optional<PaymentController.CheckoutResponse> existing =
                    verifyExistingPayment(booking);
            if (existing.isPresent()) {
                return ResponseEntity.ok(existing.get());
            }
            status = booking.getPaymentStatus();
        }
        if (status != PaymentStatus.UNPAID && status != PaymentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle booking is not eligible for payment.");
        }
        if (!"USD".equalsIgnoreCase(booking.getCurrency())
                || booking.getTotalAmount() == null || booking.getTotalAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle booking has an invalid USD price.");
        }

        if (!hasValidQuote(booking, Instant.now())) {
            final BigDecimal rate;
            try {
                rate = conversionService.getUsdToKesRate();
            } catch (RuntimeException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Unable to obtain the USD to KES exchange rate.", ex);
            }
            if (rate == null || rate.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The exchange-rate service returned an invalid rate.");
            }
            booking.setKesPaymentQuote(
                    rate, "pricing-conversion-service", Instant.now().plusSeconds(QUOTE_VALIDITY_SECONDS));
        }

        final PaymentProvider.CheckoutSession session;
        try {
            session = paymentProvider.createCarCheckout(
                    booking.getId(), booking.getPaymentAmountKes(), PAYMENT_CURRENCY, customerEmail);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Unable to initialize payment with the payment provider.", ex);
        }
        if (session == null || session.reference() == null || session.reference().isBlank()
                || session.checkoutUrl() == null
                || !session.checkoutUrl().startsWith("https://checkout.paystack.com/")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider returned an invalid checkout session.");
        }

        booking.setPaymentReferenceSafe(session.reference().trim());
        booking.setPaymentCheckoutUrl(session.checkoutUrl().trim());
        booking.setPaymentStatus(PaymentStatus.INITIATED);
        bookings.save(booking);
        return ResponseEntity.ok(response(booking, session.checkoutUrl().trim(), PaymentStatus.INITIATED));
    }

    private Optional<PaymentController.CheckoutResponse> verifyExistingPayment(CarBooking booking) {
        String reference = booking.getPaymentReference();
        if (reference == null || reference.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Existing payment has no reference.");
        }

        final PaymentProvider.PaymentVerification verification;
        try {
            verification = paymentProvider.verifyTransaction(reference.trim());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Unable to verify the existing payment. Please try again.", ex);
        }
        if (verification == null || !reference.trim().equals(verification.reference())
                || !verification.isCarBooking()
                || !booking.getId().equals(verification.bookingId())
                || verification.currency() == null
                || !PAYMENT_CURRENCY.equals(verification.currency().trim().toUpperCase(Locale.ROOT))
                || booking.getPaymentAmountKes() == null
                || verification.amountMinor() != booking.getPaymentAmountKes()
                        .setScale(2).movePointRight(2).longValueExact()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment verification did not match this vehicle booking.");
        }

        if (verification.isSuccessful()) {
            CarBooking updated = completionService.completeCarVerified(verification, reference.trim());
            booking.setPaymentStatus(updated.getPaymentStatus());
            return Optional.of(response(booking, null, updated.getPaymentStatus()));
        }
        if ("failed".equalsIgnoreCase(verification.status())) {
            booking.setPaymentStatus(PaymentStatus.FAILED);
            bookings.save(booking);
            return Optional.empty();
        }

        String checkoutUrl = booking.getPaymentCheckoutUrl();
        if (checkoutUrl == null || !checkoutUrl.startsWith("https://checkout.paystack.com/")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Existing checkout URL is unavailable.");
        }
        return Optional.of(response(booking, checkoutUrl, PaymentStatus.INITIATED));
    }

    private static boolean hasValidQuote(CarBooking booking, Instant now) {
        return booking.getPaymentAmountKes() != null
                && booking.getPaymentAmountKes().signum() > 0
                && booking.getExchangeRate() != null
                && booking.getExchangeRate().signum() > 0
                && booking.getExchangeRateSource() != null
                && !booking.getExchangeRateSource().isBlank()
                && PAYMENT_CURRENCY.equalsIgnoreCase(booking.getPaymentCurrency())
                && booking.getPaymentQuoteExpiresAt() != null
                && booking.getPaymentQuoteExpiresAt().isAfter(now);
    }

    private PaymentController.CheckoutResponse response(
            CarBooking booking,
            String checkoutUrl,
            PaymentStatus status
    ) {
        return new PaymentController.CheckoutResponse(
                checkoutUrl,
                booking.getPaymentReference(),
                paymentProvider.getProviderName(),
                status.name(),
                booking.getPaymentAmountKes(),
                booking.getPaymentCurrency(),
                booking.getExchangeRate(),
                booking.getExchangeRateSource(),
                booking.getPaymentQuoteExpiresAt()
        );
    }
}
