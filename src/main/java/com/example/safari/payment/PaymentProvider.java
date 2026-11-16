package com.example.safari.payment;

import java.math.BigDecimal;

public interface PaymentProvider {

    CheckoutSession createCheckout(
            Long bookingId,
            BigDecimal amount,
            String currency,
            String customerEmail
    );

    default CheckoutSession createCarCheckout(
            Long bookingId,
            BigDecimal amount,
            String currency,
            String customerEmail
    ) {
        return createCheckout(bookingId, amount, currency, customerEmail);
    }

    PaymentVerification verifyTransaction(String reference);

    String getProviderName();

    record CheckoutSession(String reference, String checkoutUrl) {}

    record PaymentVerification(
            String reference,
            String status,
            long amountMinor,
            String currency,
            Long bookingId,
            String bookingType
    ) {
        public PaymentVerification(
                String reference,
                String status,
                long amountMinor,
                String currency,
                Long bookingId
        ) {
            this(reference, status, amountMinor, currency, bookingId, "tour");
        }

        public boolean isSuccessful() {
            return "success".equalsIgnoreCase(status);
        }

        public boolean isCarBooking() {
            return "car".equalsIgnoreCase(bookingType);
        }
    }
}
