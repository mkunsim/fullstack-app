package com.example.safari.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EnquiryControllerTest {

    @Mock
    private MailOutboxService outbox;

    @Test
    void queuesEnquiryForOperatorWithoutCreatingBooking() {
        EnquiryController controller = new EnquiryController(
                outbox, "operator@example.com", "sender@example.com");
        EnquiryController.EnquiryRequest request = new EnquiryController.EnquiryRequest(
                "  Jane Traveller  ",
                " jane@example.com ",
                EnquiryController.EnquiryType.TOUR,
                " Serengeti Great Migration ",
                "Could you advise on the best travel dates?"
        );

        var response = controller.submit(request);
        EnquiryController.EnquiryResponse responseBody =
                Objects.requireNonNull(response.getBody());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("Enquiry received. No booking has been created.",
                responseBody.message());
        ArgumentCaptor<String> dedupeKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(outbox).enqueue(
                dedupeKey.capture(),
                eq("operator@example.com"),
                subject.capture(),
                body.capture()
        );
        assertTrue(dedupeKey.getValue().startsWith("enquiry:"));
        assertEquals("Website enquiry | TOUR | Serengeti Great Migration", subject.getValue());
        assertTrue(body.getValue().contains("jane@example.com"));
        assertTrue(body.getValue().contains("Could you advise on the best travel dates?"));
    }

    @Test
    void rejectsSubmissionWhenEmailDeliveryIsNotConfigured() {
        EnquiryController controller = new EnquiryController(outbox, "", "");
        EnquiryController.EnquiryRequest request = new EnquiryController.EnquiryRequest(
                "Jane", "jane@example.com", EnquiryController.EnquiryType.VEHICLE,
                "Safari vehicle", "Is a guide available?");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> controller.submit(request));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        verifyNoInteractions(outbox);
    }
}
