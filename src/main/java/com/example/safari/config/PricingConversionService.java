
package com.example.safari.config;

import com.example.safari.booking.Booking;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PricingConversionService {

    private static final BigDecimal USD_TO_KES_RATE =
            new BigDecimal("130.50");

    private static final String RATE_SOURCE =
            "Configured fixed USD/KES rate";

    private static final long QUOTE_VALIDITY_SECONDS = 3600;

    @Transactional
    public void convertBookingToKes(Booking booking) {

        if (booking == null) {
            throw new IllegalArgumentException(
                    "Booking is required."
            );
        }

        if (booking.getTotalAmount() == null
                || booking.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Booking must have a valid USD amount."
            );
        }

        if (booking.getPaymentAmountKes() != null
                && booking.getPaymentAmountKes().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                    "A KES payment quote already exists."
            );
        }

        booking.setKesPaymentQuote(
                USD_TO_KES_RATE,
                RATE_SOURCE,
                Instant.now().plusSeconds(QUOTE_VALIDITY_SECONDS)
        );
    }

    public BigDecimal getUsdToKesRate() {
        return USD_TO_KES_RATE;
    }
}
    