package com.example.safari.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaystackWebhookControllerTest {
    private static final String SECRET = "sk_test_unit_test_secret";
    @Mock PaymentCompletionService completion;
    PaystackWebhookController controller;
    ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        controller = new PaystackWebhookController(completion, mapper, SECRET);
    }

    @Test
    void validChargeSuccessWebhookCallsIndependentVerification() throws Exception {
        String body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"ref-1\"}}";
        var response = controller.handleWebhook(signature(body), body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(completion).verifyAndCompleteAny("ref-1");
    }

    @Test
    void invalidSignatureIsRejectedAndDoesNotVerifyPayment() {
        String body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"ref-1\"}}";
        var response = controller.handleWebhook("not-a-valid-signature", body);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(completion);
    }

    @Test
    void nonSuccessEventIsIgnored() throws Exception {
        String body = "{\"event\":\"charge.failed\",\"data\":{\"reference\":\"ref-1\"}}";
        var response = controller.handleWebhook(signature(body), body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verifyNoInteractions(completion);
    }

    @Test
    void repeatedSuccessWebhookIsForwardedAndCompletionServiceMustBeIdempotent() throws Exception {
        String body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"ref-1\"}}";
        controller.handleWebhook(signature(body), body);
        controller.handleWebhook(signature(body), body);

        verify(completion, times(2)).verifyAndCompleteAny("ref-1");
    }

    @Test
    void processingFailureReturnsServerErrorSoProviderCanRetry() throws Exception {
        String body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"ref-1\"}}";
        doThrow(new IllegalStateException("verification failed"))
                .when(completion).verifyAndCompleteAny("ref-1");

        var response = controller.handleWebhook(signature(body), body);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    private String signature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
