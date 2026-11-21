
package com.example.safari.payment;

import com.example.safari.booking.*;
import com.example.safari.car.CarBooking;
import com.example.safari.car.CarBookingRepository;
import com.example.safari.car.CarBookingStatus;
import com.example.safari.security.*;
import com.example.safari.tour.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCompletionServiceTest {

    @Mock
    BookingRepository bookingRepository;

    @Mock
    CarBookingRepository carBookingRepository;

    @Mock
    PaymentProvider paymentProvider;

    private PaymentCompletionService service;
    @NonNull
    private final Booking booking = Objects.requireNonNull(createBooking());

    @BeforeEach
    void setUp() {
        service = new PaymentCompletionService(
                bookingRepository,
                carBookingRepository,
                paymentProvider
        );
    }

    private static Booking createBooking() {
        AppUser customer = AppUser.builder()
                .email("guest@example.com")
                .passwordHash("hash")
                .fullName("Guest")
                .role(Role.CUSTOMER)
                .build();
        Departure departure = Departure.builder()
                .startDate(LocalDate.now().plusDays(30))
                .capacity(8)
                .build();
        departure.reserve(2);
        Booking booking = Objects.requireNonNull(Booking.builder()
                .id(11L)
                .customer(customer)
                .departure(departure)
                .guests(2)
                .totalAmount(new BigDecimal("250.00"))
                .status(BookingStatus.CONFIRMED)
                .build());

        // Set the KES quote BEFORE initiating payment.
        booking.setKesPaymentQuote(
                new BigDecimal("130.000000"),
                "test-rate",
                Instant.now().plusSeconds(3600)
        );

        booking.setPaymentReference("ref-1");

        // Checkout has now been initiated.
        booking.setPaymentStatus(PaymentStatus.INITIATED);

        return booking;
    }

    private void stubBookingLookup() {
        when(bookingRepository.findById(11L))
                .thenReturn(Optional.of(booking));
    }

    private PaymentProvider.PaymentVerification successfulVerification() {
        return new PaymentProvider.PaymentVerification(
                "ref-1",
                "success",
                3250000L,
                "KES",
                11L
        );
    }

    @Test
    void completesVerifiedVehiclePayment() {
        AppUser customer = AppUser.builder()
                .email("guest@example.com")
                .passwordHash("hash")
                .fullName("Guest")
                .role(Role.CUSTOMER)
                .build();
        CarBooking carBooking = Objects.requireNonNull(CarBooking.builder()
                .id(22L)
                .customer(customer)
                .currency("USD")
                .totalAmount(new BigDecimal("150.00"))
                .status(CarBookingStatus.CONFIRMED.name())
                .paymentStatus(PaymentStatus.INITIATED)
                .paymentCurrency("KES")
                .paymentAmountKes(new BigDecimal("19575.00"))
                .paymentReference("car-ref-1")
                .build());
        PaymentProvider.PaymentVerification verification =
                new PaymentProvider.PaymentVerification(
                        "car-ref-1", "success", 1957500L, "KES", 22L, "car");
        when(carBookingRepository.findByIdForUpdate(22L))
                .thenReturn(Optional.of(carBooking));
        when(carBookingRepository.save(carBooking)).thenReturn(carBooking);

        CarBooking result = service.completeCarVerified(verification, "car-ref-1");

        assertSame(carBooking, result);
        assertEquals(PaymentStatus.PAID, result.getPaymentStatus());
        verify(carBookingRepository).save(carBooking);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void verifiedSuccessfulPaymentMarksBookingPaid() {
        stubBookingLookup();

        when(paymentProvider.verifyTransaction("ref-1"))
                .thenReturn(successfulVerification());

        when(bookingRepository.save(booking))
                .thenReturn(booking);

        Booking result = service.verifyAndComplete("ref-1");

        assertNotNull(result);
        assertEquals(PaymentStatus.PAID, result.getPaymentStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    void failedPaystackPaymentDoesNotMarkBookingPaid() {
        when(paymentProvider.verifyTransaction("ref-1"))
                .thenReturn(new PaymentProvider.PaymentVerification(
                        "ref-1", "failed", 3250000L, "KES", 11L
                ));

        assertThrows(
                IllegalStateException.class,
                () -> service.verifyAndComplete("ref-1")
        );

        assertEquals(PaymentStatus.INITIATED, booking.getPaymentStatus());
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void mismatchedAmountIsRejected() {
        stubBookingLookup();

        when(paymentProvider.verifyTransaction("ref-1"))
                .thenReturn(new PaymentProvider.PaymentVerification(
                        "ref-1", "success", 3249999L, "KES", 11L
                ));

        assertThrows(
                IllegalStateException.class,
                () -> service.verifyAndComplete("ref-1")
        );

        assertEquals(PaymentStatus.INITIATED, booking.getPaymentStatus());
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    void mismatchedCurrencyIsRejected() {
        stubBookingLookup();

        when(paymentProvider.verifyTransaction("ref-1"))
                .thenReturn(new PaymentProvider.PaymentVerification(
                        "ref-1", "success", 3250000L, "USD", 11L
                ));

        assertThrows(
                IllegalStateException.class,
                () -> service.verifyAndComplete("ref-1")
        );

        assertEquals(PaymentStatus.INITIATED, booking.getPaymentStatus());
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    void mismatchedReferenceIsRejected() {
        when(paymentProvider.verifyTransaction("ref-1"))
                .thenReturn(new PaymentProvider.PaymentVerification(
                        "different-ref",
                        "success",
                        3250000L,
                        "KES",
                        11L
                ));

        assertThrows(
                IllegalStateException.class,
                () -> service.verifyAndComplete("ref-1")
        );

        verifyNoInteractions(bookingRepository);
    }

    @Test
    void repeatedSuccessfulVerificationIsIdempotent() {
        booking.setPaymentStatus(PaymentStatus.PAID);
        stubBookingLookup();

        when(paymentProvider.verifyTransaction("ref-1"))
                .thenReturn(successfulVerification());

        Booking result = service.verifyAndComplete("ref-1");

        assertEquals(PaymentStatus.PAID, result.getPaymentStatus());
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    void cannotCompletePaymentBeforeCheckoutWasInitiated() {
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        stubBookingLookup();

        when(paymentProvider.verifyTransaction("ref-1"))
                .thenReturn(successfulVerification());

        assertThrows(
                IllegalStateException.class,
                () -> service.verifyAndComplete("ref-1")
        );

        assertEquals(PaymentStatus.UNPAID, booking.getPaymentStatus());
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    void shouldRejectWhenPaystackReturnsDifferentReference() {
        when(paymentProvider.verifyTransaction("booking-ref-123"))
                .thenReturn(new PaymentProvider.PaymentVerification(
                        "different-ref-456",
                        "success",
                        3250000L,
                        "KES",
                        11L
                ));

        assertThrows(
                IllegalStateException.class,
                () -> service.verifyAndComplete("booking-ref-123")
        );

        verifyNoInteractions(bookingRepository);
    }
}
