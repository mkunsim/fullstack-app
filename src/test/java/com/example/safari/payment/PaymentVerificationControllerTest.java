
package com.example.safari.payment;

import com.example.safari.booking.Booking;
import com.example.safari.booking.BookingRepository;
import com.example.safari.car.CarBooking;
import com.example.safari.car.CarBookingRepository;
import com.example.safari.security.PasswordlessAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentVerificationControllerTest {

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private PaymentCompletionService paymentCompletionService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CarBookingRepository carBookingRepository;

    @Mock
    private PasswordlessAuthService passwordlessAuthService;

    @Mock
    private HttpServletRequest httpRequest;

    private PaymentVerificationController controller;

    private Booking booking;

    private PaymentProvider.PaymentVerification verification;

    private static final String REFERENCE = "ref-1";
    private static final String CUSTOMER_EMAIL = "customer@example.com";
    private static final long BOOKING_ID = 11L;

    @BeforeEach
    void setUp() {
        controller = new PaymentVerificationController(
                paymentProvider,
                paymentCompletionService,
                bookingRepository,
                carBookingRepository,
                passwordlessAuthService
        );

        // Deep stubbing avoids depending on the concrete Customer constructor.
        booking = mock(Booking.class, RETURNS_DEEP_STUBS);

        verification = new PaymentProvider.PaymentVerification(
                REFERENCE,
                "success",
                3250000L,
                "KES",
                BOOKING_ID
        );

        when(passwordlessAuthService.customerEmailFromRequest(httpRequest))
                .thenReturn(CUSTOMER_EMAIL);
    }

    @Test
    void customerCanVerifyOwnSuccessfulPayment() {
        when(paymentProvider.verifyTransaction(REFERENCE))
                .thenReturn(verification);

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking));

        when(booking.getCustomer().getEmail())
                .thenReturn(CUSTOMER_EMAIL);

        when(booking.getId()).thenReturn(BOOKING_ID);
        when(booking.getPaymentStatus()).thenReturn(PaymentStatus.PAID);

        when(paymentCompletionService.completeVerified(
                verification, REFERENCE
        )).thenReturn(booking);

        PaymentVerificationController.VerifyPaymentRequest request =
                new PaymentVerificationController.VerifyPaymentRequest(REFERENCE);

        ResponseEntity<Map<String, Object>> response =
                controller.verifyPayment(request, httpRequest);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = Objects.requireNonNull(response.getBody());

        assertEquals("Payment verified", body.get("message"));
        assertEquals(BOOKING_ID, body.get("bookingId"));
        assertEquals("PAID", body.get("paymentStatus"));

        verify(paymentProvider).verifyTransaction(REFERENCE);

        verify(bookingRepository).findById(BOOKING_ID);

        verify(paymentCompletionService).completeVerified(
                verification, REFERENCE
        );

        // The transaction must not be verified a second time.
        verify(paymentCompletionService, never())
                .verifyAndComplete(anyString());
    }

    @Test
    void customerCanVerifyOwnVehiclePayment() {
        PaymentProvider.PaymentVerification carVerification =
                new PaymentProvider.PaymentVerification(
                        REFERENCE, "success", 3250000L, "KES", BOOKING_ID, "car");
        CarBooking carBooking = mock(CarBooking.class, RETURNS_DEEP_STUBS);
        when(paymentProvider.verifyTransaction(REFERENCE)).thenReturn(carVerification);
        when(carBookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(carBooking));
        when(carBooking.getCustomer().getEmail()).thenReturn(CUSTOMER_EMAIL);
        when(carBooking.getId()).thenReturn(BOOKING_ID);
        when(carBooking.getPaymentStatus()).thenReturn(PaymentStatus.PAID);
        when(paymentCompletionService.completeCarVerified(carVerification, REFERENCE))
                .thenReturn(carBooking);

        ResponseEntity<Map<String, Object>> response = controller.verifyPayment(
                new PaymentVerificationController.VerifyPaymentRequest(REFERENCE), httpRequest);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(BOOKING_ID, Objects.requireNonNull(response.getBody()).get("carBookingId"));
        verify(paymentCompletionService).completeCarVerified(carVerification, REFERENCE);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void customerCannotVerifyAnotherCustomersPayment() {
        when(paymentProvider.verifyTransaction(REFERENCE))
                .thenReturn(verification);

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking));

        when(booking.getCustomer().getEmail())
                .thenReturn("another@example.com");

        PaymentVerificationController.VerifyPaymentRequest request =
                new PaymentVerificationController.VerifyPaymentRequest(REFERENCE);

        assertThrows(
                ResponseStatusException.class,
                () -> controller.verifyPayment(request, httpRequest)
        );

        verify(paymentProvider).verifyTransaction(REFERENCE);
        verify(bookingRepository).findById(BOOKING_ID);

        verify(paymentCompletionService, never())
                .completeVerified(any(), anyString());

        verify(paymentCompletionService, never())
                .verifyAndComplete(anyString());
    }

    @Test
    void unauthenticatedCustomerCannotVerifyPayment() {
        when(passwordlessAuthService.customerEmailFromRequest(httpRequest))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication token"));

        PaymentVerificationController.VerifyPaymentRequest request =
                new PaymentVerificationController.VerifyPaymentRequest(REFERENCE);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.verifyPayment(request, httpRequest)
        );

        assertEquals(401, exception.getStatusCode().value());

        verifyNoInteractions(paymentProvider);
        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(paymentCompletionService);
    }

    @Test
    void customerCannotVerifyWithMissingReference() {
        PaymentVerificationController.VerifyPaymentRequest request =
                new PaymentVerificationController.VerifyPaymentRequest(" ");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.verifyPayment(request, httpRequest)
        );

        assertEquals(400, exception.getStatusCode().value());

        verifyNoInteractions(paymentProvider);
        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(paymentCompletionService);
    }
}
