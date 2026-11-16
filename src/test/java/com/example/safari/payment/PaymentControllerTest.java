package com.example.safari.payment;

import com.example.safari.booking.Booking;
import com.example.safari.booking.BookingRepository;
import com.example.safari.booking.BookingStatus;
import com.example.safari.config.PricingConversionService;
import com.example.safari.payment.PaymentController.CheckoutResponse;
import com.example.safari.security.AppUser;
import com.example.safari.security.PasswordlessAuthService;
import com.example.safari.security.Role;
import com.example.safari.tour.Departure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final long BOOKING_ID = 42L;
    private static final String CUSTOMER_EMAIL = "traveller@example.com";
    private static final String CHECKOUT_URL =
            "https://checkout.paystack.com/test-session";
    private static final String PAYMENT_REFERENCE = "wildpath-42-test123";
    private static final String AUTH_TOKEN = "test-token";

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private PasswordlessAuthService passwordlessAuthService;

    @Mock
    private PaymentCompletionService paymentCompletionService;

    @Mock
    private PricingConversionService pricingConversionService;

    private PaymentController paymentController;

    private final Booking pendingBooking = createBooking(BookingStatus.PENDING, false);

    private final Booking confirmedBooking = createBooking(BookingStatus.CONFIRMED, true);

    @BeforeEach
    void setUp() {
        paymentController = new PaymentController(
                bookingRepository,
                paymentProvider,
                paymentCompletionService,
                passwordlessAuthService,
                pricingConversionService
        );
    }

    private static Booking createBooking(BookingStatus status, boolean includeKesQuote) {
        AppUser customer = Objects.requireNonNull(AppUser.builder()
                .email(CUSTOMER_EMAIL)
                .passwordHash("not-a-real-password-hash")
                .fullName("Test Traveller")
                .role(Role.CUSTOMER)
                .build());

        Departure departure = Objects.requireNonNull(Departure.builder()
                .startDate(LocalDate.now().plusMonths(2))
                .capacity(12)
                .build());

        departure.reserve(2);
        Booking booking = Objects.requireNonNull(Booking.builder()
                .id(BOOKING_ID)
                .customer(customer)
                .departure(departure)
                .guests(2)
                .totalAmount(new BigDecimal("250.00"))
                .status(status)
                .build());

        if (includeKesQuote) {
            booking.setKesPaymentQuote(
                    new BigDecimal("130.000000"),
                    "test-rate",
                    Instant.now().plusSeconds(3600)
            );
        }
        return booking;
    }

    @Test
    void customerCannotStartCheckoutWithoutSigningIn() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(passwordlessAuthService.customerEmailFromRequest(request))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentController.checkout(BOOKING_ID, request)
        );

        assertEquals(401, exception.getStatusCode().value());
        verifyNoInteractions(paymentProvider);
    }

    @Test
    void customerCannotPayForAnotherCustomersBooking() {
        MockHttpServletRequest request = authenticatedRequest();

        when(passwordlessAuthService.customerEmailFromRequest(request)).thenReturn("someone-else@example.com");

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(confirmedBooking));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentController.checkout(BOOKING_ID, request)
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(paymentProvider);
    }

    @Test
    void pendingBookingCannotStartCheckout() {
        MockHttpServletRequest request = authenticatedRequest();

        when(passwordlessAuthService.customerEmailFromRequest(request)).thenReturn(CUSTOMER_EMAIL);

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(pendingBooking));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentController.checkout(BOOKING_ID, request)
        );

        assertEquals(409, exception.getStatusCode().value());

        assertEquals(
                PaymentStatus.UNPAID,
                pendingBooking.getPaymentStatus()
        );

        verifyNoInteractions(paymentProvider);
    }

    @Test
    void confirmedBookingWithKesQuoteCreatesCheckoutAndBecomesInitiated() {
        MockHttpServletRequest request = authenticatedRequest();

        when(passwordlessAuthService.customerEmailFromRequest(request)).thenReturn(CUSTOMER_EMAIL);

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(confirmedBooking));

        when(paymentProvider.createCheckout(
                eq(BOOKING_ID),
                eq(new BigDecimal("32500.00")),
                eq("KES"),
                eq(CUSTOMER_EMAIL)
        )).thenReturn(new PaymentProvider.CheckoutSession(
                PAYMENT_REFERENCE,
                CHECKOUT_URL
        ));

        when(paymentProvider.getProviderName()).thenReturn("paystack");

        ResponseEntity<CheckoutResponse> response =
                paymentController.checkout(BOOKING_ID, request);

        CheckoutResponse body = Objects.requireNonNull(response.getBody());
        assertEquals(CHECKOUT_URL, body.checkoutUrl());
        assertEquals(PAYMENT_REFERENCE, body.reference());
        assertEquals("paystack", body.provider());
        assertEquals("INITIATED", body.status());

        assertEquals(
                PAYMENT_REFERENCE,
                confirmedBooking.getPaymentReference()
        );

        assertEquals(
                PaymentStatus.INITIATED,
                confirmedBooking.getPaymentStatus()
        );

        verify(paymentProvider).createCheckout(
                BOOKING_ID,
                new BigDecimal("32500.00"),
                "KES",
                CUSTOMER_EMAIL
        );

        verify(bookingRepository).save(confirmedBooking);

        verifyNoInteractions(pricingConversionService);
    }

    @Test
    void alreadyPaidBookingCannotStartAnotherCheckout() {
        confirmedBooking.setPaymentStatus(PaymentStatus.PAID);

        MockHttpServletRequest request = authenticatedRequest();

        when(passwordlessAuthService.customerEmailFromRequest(request)).thenReturn(CUSTOMER_EMAIL);

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(confirmedBooking));

        ResponseEntity<CheckoutResponse> response =
                paymentController.checkout(BOOKING_ID, request);

        assertEquals(200, response.getStatusCode().value());

        CheckoutResponse body = Objects.requireNonNull(response.getBody());
        assertEquals("PAID", body.status());
        assertNull(body.checkoutUrl());

        verify(paymentProvider, never()).createCheckout(
                anyLong(),
                any(BigDecimal.class),
                anyString(),
                anyString()
        );
    }

    @Test
    void initiatedPaymentCannotStartDuplicateCheckout() {
        confirmedBooking.setPaymentStatus(PaymentStatus.INITIATED);

        MockHttpServletRequest request = authenticatedRequest();

        when(passwordlessAuthService.customerEmailFromRequest(request)).thenReturn(CUSTOMER_EMAIL);

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(confirmedBooking));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentController.checkout(BOOKING_ID, request)
        );

        assertEquals(409, exception.getStatusCode().value());
        verifyNoInteractions(paymentProvider);
    }

    @Test
    void bookingWithoutKesPaymentQuoteGeneratesQuoteAndStartsCheckout() {
        confirmedBooking.setPaymentAmountKes(null);

        MockHttpServletRequest request = authenticatedRequest();

        when(passwordlessAuthService.customerEmailFromRequest(request)).thenReturn(CUSTOMER_EMAIL);

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(confirmedBooking));

        when(pricingConversionService.getUsdToKesRate())
                .thenReturn(new BigDecimal("130.000000"));

        when(paymentProvider.createCheckout(
                eq(BOOKING_ID),
                eq(new BigDecimal("32500.00")),
                eq("KES"),
                eq(CUSTOMER_EMAIL)
        )).thenReturn(new PaymentProvider.CheckoutSession(
                PAYMENT_REFERENCE,
                CHECKOUT_URL
        ));

        when(paymentProvider.getProviderName()).thenReturn("paystack");

        ResponseEntity<CheckoutResponse> response =
                paymentController.checkout(BOOKING_ID, request);

        CheckoutResponse body = Objects.requireNonNull(response.getBody());
        assertEquals(CHECKOUT_URL, body.checkoutUrl());

        assertEquals(
                new BigDecimal("32500.00"),
                confirmedBooking.getPaymentAmountKes()
        );

        assertEquals(
                new BigDecimal("130.000000"),
                confirmedBooking.getExchangeRate()
        );

        assertEquals("KES", confirmedBooking.getPaymentCurrency());

        assertEquals(
                PaymentStatus.INITIATED,
                confirmedBooking.getPaymentStatus()
        );

        verify(pricingConversionService).getUsdToKesRate();

        verify(paymentProvider).createCheckout(
                BOOKING_ID,
                new BigDecimal("32500.00"),
                "KES",
                CUSTOMER_EMAIL
        );

        verify(bookingRepository).save(confirmedBooking);
    }

    @Test
    void bookingMustExist() {
        MockHttpServletRequest request = authenticatedRequest();

        when(passwordlessAuthService.customerEmailFromRequest(request)).thenReturn(CUSTOMER_EMAIL);

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentController.checkout(BOOKING_ID, request)
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(paymentProvider);
    }

    @Test
    void invalidCheckoutSessionIsRejected() {
        MockHttpServletRequest request = authenticatedRequest();

        when(passwordlessAuthService.customerEmailFromRequest(request)).thenReturn(CUSTOMER_EMAIL);

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(confirmedBooking));

        when(paymentProvider.createCheckout(
                anyLong(),
                any(BigDecimal.class),
                anyString(),
                anyString()
        )).thenReturn(
                new PaymentProvider.CheckoutSession("", "")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentController.checkout(BOOKING_ID, request)
        );

        assertEquals(502, exception.getStatusCode().value());

        assertEquals(
                PaymentStatus.UNPAID,
                confirmedBooking.getPaymentStatus()
        );

        verify(bookingRepository, never()).save(confirmedBooking);
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + AUTH_TOKEN);
        return request;
    }
}