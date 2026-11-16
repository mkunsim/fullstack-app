package com.example.safari.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/payments")
public class PaystackWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaystackWebhookController.class);

    private final PaymentCompletionService paymentCompletionService;
    private final ObjectMapper objectMapper;
    private final String secretKey;

    public PaystackWebhookController(
            PaymentCompletionService paymentCompletionService,
            ObjectMapper objectMapper,
            @Value("${app.payment.paystack.secret-key:}") String secretKey
    ) {
        this.paymentCompletionService = paymentCompletionService;
        this.objectMapper = objectMapper;
        this.secretKey = secretKey == null ? "" : secretKey;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "x-paystack-signature", required = false)
            String signature,
            @RequestBody String rawBody
    ) {

        if (!isValidSignature(rawBody, signature)) {
            log.warn("Rejected Paystack webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid webhook signature");
        }
        try {
            JsonNode event = objectMapper.readTree(rawBody);
            if (!"charge.success".equals(event.path("event").asText())) {
                return ResponseEntity.ok("Event ignored");
            }
            String reference = event.path("data").path("reference").asText("");
            if (reference.isBlank()) {
                return ResponseEntity.badRequest().body("Missing payment reference");
            }
            paymentCompletionService.verifyAndCompleteAny(reference);
            return ResponseEntity.ok("Webhook processed");
        } catch (Exception ex) {
            // Non-2xx prompts Paystack to retry; avoid acknowledging an unprocessed event.
            log.error("Paystack webhook processing failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook processing failed");
        }
    }

    private boolean isValidSignature(String rawBody, String suppliedSignature) {
        if (secretKey.isBlank() || suppliedSignature == null || suppliedSignature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"
            ));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] expected = HexFormat.of().formatHex(digest)
                    .getBytes(StandardCharsets.US_ASCII);
            byte[] supplied = suppliedSignature.trim().toLowerCase()
                    .getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, supplied);
        } catch (Exception ex) {
            return false;
        }
    }
}
