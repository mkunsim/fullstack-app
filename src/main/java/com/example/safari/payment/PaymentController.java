package com.example.safari.payment;

import com.example.safari.booking.Booking;
import com.example.safari.booking.BookingRepository;
import com.example.safari.booking.BookingStatus;
import com.example.safari.config.PricingConversionService;
import com.example.safari.security.PasswordlessAuthService;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final BookingRepository bookingRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentCompletionService paymentCompletionService;
    private final PasswordlessAuthService passwordlessAuthService;
    private final PricingConversionService exchangeRateService;

    public PaymentController(
            BookingRepository bookingRepository,
            PaymentProvider paymentProvider,
            PaymentCompletionService paymentCompletionService,
            PasswordlessAuthService passwordlessAuthService,
            PricingConversionService exchangeRateService
    ) {
        this.bookingRepository = bookingRepository;
        this.paymentProvider = paymentProvider;
        this.paymentCompletionService = paymentCompletionService;
        this.passwordlessAuthService = passwordlessAuthService;
        this.exchangeRateService = exchangeRateService;
    }

    public record CheckoutResponse(
            String checkoutUrl,
            String reference,
            String provider,
            String status,
            BigDecimal paymentAmount,
            String paymentCurrency,
            BigDecimal exchangeRate,
            String exchangeRateSource,
            Instant quoteExpiresAt
    ) {
        public CheckoutResponse(String checkoutUrl, String reference, String provider, String status) {
            this(checkoutUrl, reference, provider, status, null, null, null, null, null);
        }
    }

    @PostMapping("/bookings/{bookingId}/checkout")
    @Transactional
    public ResponseEntity<CheckoutResponse> checkout(
            @PathVariable long bookingId,
            HttpServletRequest request
    ) {

        // 1. Authenticate customer.

        String customerEmail;

        try {
            customerEmail = passwordlessAuthService.customerEmailFromRequest(request);

        } catch (RuntimeException ex) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in before paying."
            );
        }

        if (customerEmail == null || customerEmail.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please sign in before paying."
            );
        }

        customerEmail = customerEmail.trim();

        // 2. Find booking.

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> {

                    return new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Booking not found."
                    );
                });

        // 3. Verify booking ownership.

        if (booking.getCustomer() == null
                || booking.getCustomer().getEmail() == null
                || !customerEmail.equalsIgnoreCase(
                        booking.getCustomer().getEmail()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Booking not found."
            );
        }

        // 4. Booking must be confirmed.

        if (booking.getStatus() != BookingStatus.CONFIRMED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking must be confirmed before payment."
            );
        }

        // 5. Validate payment status.

        PaymentStatus currentStatus = booking.getPaymentStatus();

        if (currentStatus == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking has no valid payment status."
            );
        }

        if (currentStatus == PaymentStatus.PAID) {

            return ResponseEntity.ok(
                    new CheckoutResponse(
                            null,
                            booking.getPaymentReference(),
                            paymentProvider.getProviderName(),
                            PaymentStatus.PAID.name()
                    )
            );
        }

        if (currentStatus == PaymentStatus.REFUNDED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This booking has already been refunded."
            );
        }

        /*
         * 6. If payment is INITIATED, verify the existing Paystack
         * transaction before deciding whether to reuse or replace it.
         */

        if (currentStatus == PaymentStatus.INITIATED) {

            String existingReference = booking.getPaymentReference();
            String existingCheckoutUrl = booking.getPaymentCheckoutUrl();

            if (existingReference == null
                    || existingReference.isBlank()) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Existing payment has no reference and requires investigation."
                );
            }

            existingReference = existingReference.trim();

            final PaymentProvider.PaymentVerification verification;

            try {

                verification =
                        paymentProvider.verifyTransaction(existingReference);

            } catch (RuntimeException ex) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Unable to verify the existing payment with Paystack. "
                                + "Please try again shortly.",
                        ex
                );
            }

            if (verification == null) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Paystack returned no payment verification data."
                );
            }

            // Validate transaction identity, currency and amount.

            validateExistingVerification(
                    booking,
                    existingReference,
                    verification
            );

            // 7. Existing transaction was successful.

            if ("success".equalsIgnoreCase(verification.status())) {

                try {

                    paymentCompletionService.completeVerified(
                            verification,
                            existingReference
                    );

                } catch (RuntimeException ex) {

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Paystack reports a successful payment, but the "
                                    + "booking could not be updated. Please contact support.",
                            ex
                    );
                }

                return ResponseEntity.ok(
                        new CheckoutResponse(
                                null,
                                existingReference,
                                paymentProvider.getProviderName(),
                                PaymentStatus.PAID.name()
                        )
                );
            }

            // 8. Existing transaction definitively failed.

            if ("failed".equalsIgnoreCase(verification.status())) {

                // Do not clear the reference or checkout URL here.
                // Booking.setPaymentReference() does not accept null.
                booking.setPaymentStatus(PaymentStatus.FAILED);
                currentStatus = PaymentStatus.FAILED;

            } else {

                /*
                 * Pending, processing or unknown statuses must not
                 * trigger a second checkout.
                 */

                if (existingCheckoutUrl == null
                        || existingCheckoutUrl.isBlank()
                        || !existingCheckoutUrl.trim().startsWith(
                                "https://checkout.paystack.com/"
                        )) {

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Payment is still being processed, but its checkout "
                                    + "URL is unavailable. Please contact support."
                    );
                }

                return ResponseEntity.ok(
                        new CheckoutResponse(
                                existingCheckoutUrl.trim(),
                                existingReference,
                                paymentProvider.getProviderName(),
                                PaymentStatus.INITIATED.name(),
                                booking.getPaymentAmountKes(),
                                booking.getPaymentCurrency(),
                                booking.getExchangeRate(),
                                booking.getExchangeRateSource(),
                                booking.getPaymentQuoteExpiresAt()
                        )
                );
            }
        }

        /*
         * Only UNPAID and FAILED payments can create a new checkout.
         */

        if (currentStatus != PaymentStatus.UNPAID
                && currentStatus != PaymentStatus.FAILED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking is not eligible for payment."
            );
        }

        // 9. Original tour price must remain USD.

        if (booking.getCurrency() == null
                || !"USD".equalsIgnoreCase(booking.getCurrency().trim())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking must have a valid USD tour price."
            );
        }

        BigDecimal usdAmount = booking.getTotalAmount();

        if (usdAmount == null || usdAmount.signum() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking has an invalid USD price."
            );
        }

        // 10. Check whether an existing KES quote can be reused.

        Instant now = Instant.now();

        boolean validExistingQuote =
                booking.getPaymentAmountKes() != null
                && booking.getPaymentAmountKes().signum() > 0
                && booking.getExchangeRate() != null
                && booking.getExchangeRate().signum() > 0
                && booking.getExchangeRateSource() != null
                && !booking.getExchangeRateSource().isBlank()
                && booking.getPaymentCurrency() != null
                && "KES".equalsIgnoreCase(
                        booking.getPaymentCurrency().trim()
                )
                && booking.getPaymentQuoteExpiresAt() != null
                && booking.getPaymentQuoteExpiresAt().isAfter(now);

        // 11. Generate a new quote if necessary.

        if (!validExistingQuote) {

            final BigDecimal exchangeRate;

            try {

                exchangeRate = exchangeRateService.getUsdToKesRate();

            } catch (RuntimeException ex) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Unable to obtain the USD to KES exchange rate.",
                        ex
                );
            }

            if (exchangeRate == null || exchangeRate.signum() <= 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The exchange-rate service returned an invalid rate."
                );
            }

            try {

                booking.setKesPaymentQuote(
                        exchangeRate,
                        "pricing-conversion-service",
                        Instant.now().plusSeconds(3600)
                );

            } catch (RuntimeException ex) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Unable to create the KES payment quote.",
                        ex
                );
            }

        } else {

        }

        // 12. Validate KES payment quote.

        if (booking.getPaymentAmountKes() == null
                || booking.getPaymentAmountKes().signum() <= 0
                || booking.getExchangeRate() == null
                || booking.getExchangeRate().signum() <= 0
                || booking.getPaymentCurrency() == null
                || !"KES".equalsIgnoreCase(
                        booking.getPaymentCurrency().trim()
                )
                || booking.getPaymentQuoteExpiresAt() == null
                || !booking.getPaymentQuoteExpiresAt().isAfter(
                        Instant.now()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking does not have a valid KES payment quote."
            );
        }

        // 13. Create a new Paystack checkout.

        final PaymentProvider.CheckoutSession session;

        try {

            session = paymentProvider.createCheckout(
                    booking.getId(),
                    booking.getPaymentAmountKes(),
                    "KES",
                    customerEmail
            );

        } catch (RuntimeException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to initialize payment with the payment provider.",
                    ex
            );
        }

        // 14. Validate the returned checkout session.

        if (session == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider returned an empty checkout session."
            );
        }

        /*
         * Extract the values once. Do not call session.reference()
         * or session.checkoutUrl() directly when saving the booking.
         */

        String newReference = session.reference();
        String newCheckoutUrl = session.checkoutUrl();

        if (newReference == null || newReference.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider returned an empty payment reference."
            );
        }

        if (newCheckoutUrl == null || newCheckoutUrl.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider returned an empty checkout URL."
            );
        }

        // Normalize the returned values.

        newReference = newReference.trim();
        newCheckoutUrl = newCheckoutUrl.trim();

        if (!newCheckoutUrl.startsWith(
                "https://checkout.paystack.com/"
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider returned an invalid checkout URL."
            );
        }

        if (newReference.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider returned an invalid payment reference."
            );
        }

        // 15. Save the new payment initiation details.

        try {

            booking.setPaymentReference(newReference);

            booking.setPaymentCheckoutUrl(newCheckoutUrl);

            booking.setPaymentStatus(PaymentStatus.INITIATED);

            bookingRepository.save(booking);

        } catch (IllegalArgumentException ex) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to save the new payment reference. "
                            + "Please contact support.",
                    ex
            );

        } catch (RuntimeException ex) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to save payment initiation details.",
                    ex
            );
        }

        // 16. Return checkout details.

        return ResponseEntity.ok(
                new CheckoutResponse(
                        newCheckoutUrl,
                        newReference,
                        paymentProvider.getProviderName(),
                        PaymentStatus.INITIATED.name(),
                        booking.getPaymentAmountKes(),
                        booking.getPaymentCurrency(),
                        booking.getExchangeRate(),
                        booking.getExchangeRateSource(),
                        booking.getPaymentQuoteExpiresAt()
                )
        );
    }

    /**
     * Validate that an existing Paystack transaction belongs to this
     * booking and has the expected KES amount.
     *
     * This validation runs before handling either success or failure.
     */
    private void validateExistingVerification(
            Booking booking,
            String expectedReference,
            PaymentProvider.PaymentVerification verification
    ) {

        if (verification.reference() == null
                || !expectedReference.equals(
                        verification.reference().trim()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Paystack transaction reference does not match."
            );
        }

        if (verification.bookingId() == null
                || !booking.getId().equals(verification.bookingId())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Paystack transaction does not belong to this booking."
            );
        }

        if (verification.currency() == null
                || !"KES".equals(
                        verification.currency()
                                .trim()
                                .toUpperCase(Locale.ROOT)
                )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Paystack transaction currency must be KES."
            );
        }

        if (booking.getPaymentCurrency() == null
                || !"KES".equalsIgnoreCase(
                        booking.getPaymentCurrency().trim()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking payment currency is not KES."
            );
        }

        BigDecimal expectedAmount = booking.getPaymentAmountKes();

        if (expectedAmount == null || expectedAmount.signum() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking has no valid KES payment quote."
            );
        }

        final long expectedAmountMinor;

        try {

            expectedAmountMinor = expectedAmount
                    .setScale(2)
                    .movePointRight(2)
                    .longValueExact();

        } catch (ArithmeticException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking KES payment quote is invalid.",
                    ex
            );
        }

        if (verification.amountMinor() != expectedAmountMinor) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Paystack transaction amount does not match the booking."
            );
        }

    }
}