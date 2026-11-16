package com.example.safari.payment;

import java.math.BigDecimal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("e2e")
public class E2ePaymentProviderConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock")
    PaymentProvider e2ePaymentProvider() {
        return new PaymentProvider() {
            @Override
            public CheckoutSession createCheckout(
                    Long bookingId,
                    BigDecimal amount,
                    String currency,
                    String customerEmail
            ) {
                throw new UnsupportedOperationException("Payments are disabled in the E2E profile.");
            }

            @Override
            public PaymentVerification verifyTransaction(String reference) {
                throw new UnsupportedOperationException("Payments are disabled in the E2E profile.");
            }

            @Override
            public String getProviderName() {
                return "e2e-mock";
            }
        };
    }
}
