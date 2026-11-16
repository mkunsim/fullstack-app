package com.example.safari.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "app.payment.provider",
        havingValue = "paystack"
)
public class PaystackPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PaystackPaymentProvider.class);

    private static final String API_BASE_URL = "https://api.paystack.co";
    private static final String CHECKOUT_PREFIX = "https://checkout.paystack.com/";

    private final RestClient restClient;
    private final String callbackUrl;

    public PaystackPaymentProvider(
            RestClient.Builder restClientBuilder,
            @Value("${app.payment.paystack.secret-key:}") String secretKey,
            @Value("${app.payment.paystack.callback-url:}") String callbackUrl
    ) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Paystack secret key is not configured.");
        }
        this.callbackUrl = callbackUrl == null ? "" : callbackUrl.trim();
        this.restClient = restClientBuilder
                .baseUrl(API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey.trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public CheckoutSession createCheckout(
            Long bookingId,
            BigDecimal amount,
            String currency,
            String customerEmail
    ) {
        return createCheckout(bookingId, amount, currency, customerEmail, "tour");
    }

    @Override
    public CheckoutSession createCarCheckout(
            Long bookingId,
            BigDecimal amount,
            String currency,
            String customerEmail
    ) {
        return createCheckout(bookingId, amount, currency, customerEmail, "car");
    }

    private CheckoutSession createCheckout(
            Long bookingId,
            BigDecimal amount,
            String currency,
            String customerEmail,
            String bookingType
    ) {
        if (bookingId == null || bookingId <= 0) {
            throw new IllegalArgumentException("A valid booking ID is required.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A positive payment amount is required.");
        }
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException("A customer email address is required.");
        }
        if (!"KES".equalsIgnoreCase(currency == null ? "" : currency.trim())) {
            throw new IllegalArgumentException("Paystack checkout only supports KES.");
        }
        final long amountMinor;
        try {
            amountMinor = amount.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "KES payment amount must have no more than two decimal places.", ex
            );
        }

        String reference = "wildpath-" + bookingType + "-" + bookingId + "-"
                + UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("booking_id", bookingId);
        metadata.put("booking_type", bookingType);
        metadata.put("provider", "wildpath-safaris");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", customerEmail.trim());
        payload.put("amount", amountMinor);
        payload.put("currency", "KES");
        payload.put("reference", reference);
        payload.put("metadata", metadata);
        if (!callbackUrl.isBlank()) {
            payload.put("callback_url", callbackUrl);
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/transaction/initialize")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode data = response == null ? null : response.path("data");
            String checkoutUrl = data == null ? "" : data.path("authorization_url").asText("");
            String returnedReference = data == null ? "" : data.path("reference").asText("");
            boolean success = response != null && response.path("status").asBoolean(false);

            if (!success || checkoutUrl.isBlank()
                    || !checkoutUrl.startsWith(CHECKOUT_PREFIX)
                    || !reference.equals(returnedReference)) {
                String message = response == null
                        ? "Empty response" : response.path("message").asText("Invalid response");
                throw new PaymentProviderException(
                        "Paystack could not initialize checkout: " + message
                );
            }
            log.info("Paystack checkout initialized for {} booking {}", bookingType, bookingId);
            return new CheckoutSession(returnedReference, checkoutUrl);
        } catch (HttpStatusCodeException ex) {
            throw new PaymentProviderException(
                    "Paystack checkout initialization failed. HTTP "
                            + ex.getStatusCode().value(), ex
            );
        } catch (RestClientException ex) {
            throw new PaymentProviderException(
                    "Unable to contact Paystack to initialize checkout.", ex
            );
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaymentVerification verifyTransaction(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Payment reference is required.");
        }
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/transaction/verify/{reference}", reference.trim())
                    .retrieve()
                    .body(Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("status"))) {
                throw new PaymentProviderException("Paystack could not verify the transaction.");
            }
            Object rawData = response.get("data");
            if (!(rawData instanceof Map<?, ?> rawMap)) {
                throw new PaymentProviderException("Paystack returned invalid transaction data.");
            }
            Map<String, Object> data = (Map<String, Object>) rawMap;
            String verifiedReference = stringValue(data.get("reference"));
            if (!reference.trim().equals(verifiedReference)) {
                throw new PaymentProviderException("Paystack transaction reference does not match.");
            }
            String status = stringValue(data.get("status"));
            String currency = stringValue(data.get("currency"));
            Object rawAmount = data.get("amount");
            if (!(rawAmount instanceof Number number) || number.longValue() <= 0) {
                throw new PaymentProviderException("Paystack returned an invalid transaction amount.");
            }

            Object rawMetadata = data.get("metadata");
            if (!(rawMetadata instanceof Map<?, ?> metadata)
                    || metadata.get("booking_id") == null) {
                throw new PaymentProviderException("Paystack transaction booking metadata is missing.");
            }
            String bookingType = stringValue(metadata.get("booking_type"));
            if (bookingType.isBlank()) {
                bookingType = "tour";
            }
            if (!"tour".equalsIgnoreCase(bookingType) && !"car".equalsIgnoreCase(bookingType)) {
                throw new PaymentProviderException("Paystack transaction booking type is invalid.");
            }
            final long bookingId;
            try {
                Object rawBookingId = metadata.get("booking_id");
                bookingId = rawBookingId instanceof Number id
                        ? new BigDecimal(id.toString()).longValueExact()
                        : Long.parseLong(rawBookingId.toString());
            } catch (NumberFormatException | ArithmeticException ex) {
                throw new PaymentProviderException("Paystack returned an invalid booking ID.", ex);
            }
            if (bookingId <= 0) {
                throw new PaymentProviderException("Paystack returned an invalid booking ID.");
            }
            log.debug("Paystack transaction verified for booking {}", bookingId);
            return new PaymentVerification(
                    verifiedReference, status, number.longValue(), currency, bookingId, bookingType
            );
        } catch (HttpStatusCodeException ex) {
            throw new PaymentProviderException(
                    "Paystack transaction verification failed. HTTP "
                            + ex.getStatusCode().value(), ex
            );
        } catch (RestClientException ex) {
            throw new PaymentProviderException(
                    "Unable to verify payment with Paystack.", ex
            );
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public String getProviderName() {
        return "paystack";
    }

    public static class PaymentProviderException extends RuntimeException {
        public PaymentProviderException(String message) {
            super(message);
        }

        public PaymentProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
