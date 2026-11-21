package com.example.safari.booking;

import com.example.safari.payment.PaymentStatus;
import com.example.safari.security.AppUser;
import com.example.safari.security.Role;
import com.example.safari.tour.Departure;
import com.example.safari.tour.DepartureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock BookingRepository bookings;
    @Mock BookingEmailService emailService;
    @Mock DepartureRepository departures;
    BookingService service;
    @NonNull
    private final Booking booking = Objects.requireNonNull(createBooking());

    @BeforeEach
    void setUp() {
        service = new BookingService(bookings, emailService, departures);
    }

    private static Booking createBooking() {
        AppUser customer = AppUser.builder()
                .email("guest@example.com")
                .passwordHash("hash")
                .fullName("Guest")
                .role(Role.CUSTOMER)
                .build();
        Departure departure = Departure.builder()
                .id(22L)
                .startDate(LocalDate.now().plusDays(20))
                .capacity(8)
                .build();
        departure.reserve(2);
        return Objects.requireNonNull(Booking.builder()
                .id(12L)
                .customer(customer)
                .departure(departure)
                .guests(2)
                .totalAmount(new BigDecimal("300.00"))
                .build());
    }

    @Test
    void operatorConfirmationChangesPendingToConfirmedButLeavesPaymentUnpaid() {
        when(bookings.findByIdForUpdate(12L)).thenReturn(Optional.of(booking));
        when(bookings.save(booking)).thenReturn(booking);

        Booking result = service.confirmBooking(12L);

        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals(PaymentStatus.UNPAID, result.getPaymentStatus());
        verify(bookings).save(booking);
        verify(emailService).sendStatusUpdate(booking);
    }

    @Test
    void cannotConfirmAlreadyProcessedBooking() {
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findByIdForUpdate(12L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class, () -> service.confirmBooking(12L));
        verify(bookings, never()).save(booking);
    }

    @Test
    void missingBookingCannotBeConfirmed() {
        when(bookings.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.confirmBooking(404L));
    }

    @Test
    void decliningPendingBookingReleasesDepartureSeatsAndSendsStatusEmail() {
        when(bookings.findByIdForUpdate(12L)).thenReturn(Optional.of(booking));
        when(departures.findByIdForUpdate(22L)).thenReturn(Optional.of(booking.getDeparture()));
        when(bookings.save(booking)).thenReturn(booking);
        int reservedBefore = booking.getDeparture().getReservedGuests();

        Booking result = service.updatePendingStatus(12L, BookingStatus.DECLINED);

        assertEquals(BookingStatus.DECLINED, result.getStatus());
        assertEquals(reservedBefore - booking.getGuests(), booking.getDeparture().getReservedGuests());
        verify(emailService).sendStatusUpdate(booking);
    }

    @Test
    void cancellingPendingBookingReleasesDepartureSeats() {
        booking.setActionToken("stale-action-token");
        booking.setActionTokenExpiresAt(java.time.Instant.now().plusSeconds(600));
        when(bookings.findByIdForUpdate(12L)).thenReturn(Optional.of(booking));
        when(departures.findByIdForUpdate(22L)).thenReturn(Optional.of(booking.getDeparture()));
        when(bookings.save(booking)).thenReturn(booking);
        int reservedBefore = booking.getDeparture().getReservedGuests();

        Booking result = service.cancelBooking(12L);

        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        assertEquals(reservedBefore - booking.getGuests(), booking.getDeparture().getReservedGuests());
        assertNull(result.getActionToken());
        assertNull(result.getActionTokenExpiresAt());
        verify(emailService).sendStatusUpdate(booking);
    }

    @Test
    void expiryCancelsPendingBookingAndReleasesCapacityOnce() {
        booking.setCreatedAt(java.time.Instant.now().minusSeconds(10_000));
        when(bookings.findByIdForUpdate(12L)).thenReturn(Optional.of(booking));
        when(departures.findByIdForUpdate(22L)).thenReturn(Optional.of(booking.getDeparture()));
        when(bookings.save(booking)).thenReturn(booking);
        int reservedBefore = booking.getDeparture().getReservedGuests();

        assertTrue(service.expirePendingBooking(12L, java.time.Instant.now()));
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(reservedBefore - booking.getGuests(), booking.getDeparture().getReservedGuests());
        verify(emailService).sendExpiredNotice(booking);
    }

    @Test
    void expiryDoesNotChangeBookingCreatedAfterCutoff() {
        when(bookings.findByIdForUpdate(12L)).thenReturn(Optional.of(booking));

        assertFalse(service.expirePendingBooking(12L, java.time.Instant.now().minusSeconds(3600)));
        assertEquals(BookingStatus.PENDING, booking.getStatus());
        verifyNoInteractions(departures);
        verify(emailService, never()).sendExpiredNotice(any());
    }
}
