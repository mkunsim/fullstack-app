package com.example.safari.operator;

import com.example.safari.booking.*;
import com.example.safari.config.PricingConversionService;
import com.example.safari.payment.PaymentStatus;
import com.example.safari.security.*;
import com.example.safari.tour.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperatorBookingControllerTest {
    @Mock BookingRepository bookings;
    @Mock BookingService bookingService;
    @Mock PricingConversionService pricingConversionService;
    @Test
    void validOperatorActionTokenConfirmsBookingAndInvalidatesToken() {
        AppUser customer = AppUser.builder()
                .email("guest@example.com")
                .passwordHash("hash")
                .fullName("Guest")
                .role(Role.CUSTOMER)
                .build();
        Tour tour = Tour.builder()
                .name("Safari")
                .description("Description")
                .destination("Kenya")
                .durationDays(3)
                .pricePerPerson(new BigDecimal("100"))
                .imageUrl("image")
                .build();
        Departure departure = Departure.builder()
                .tour(tour)
                .startDate(LocalDate.now().plusDays(20))
                .capacity(8)
                .build();
        departure.reserve(2);
        Booking booking = Booking.builder()
                .id(12L)
                .customer(customer)
                .departure(departure)
                .guests(2)
                .totalAmount(new BigDecimal("200"))
                .build();
        booking.setActionToken("secure-token");
        booking.setActionTokenExpiresAt(Instant.now().plusSeconds(600));
        when(bookings.findByActionTokenForUpdate("secure-token")).thenReturn(Optional.of(booking));
        when(bookingService.updatePendingStatus(12L, BookingStatus.CONFIRMED)).thenAnswer(invocation -> {
            booking.setStatus(invocation.getArgument(1));
            booking.setActionToken(null);
            booking.setActionTokenExpiresAt(null);
            return booking;
        });

        OperatorBookingController controller =
                new OperatorBookingController(bookings, bookingService, pricingConversionService);
        ResponseEntity<String> response =
                controller.handleAction("secure-token", "confirm");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(PaymentStatus.UNPAID, booking.getPaymentStatus());
        assertNull(booking.getActionToken());
        assertNull(booking.getActionTokenExpiresAt());
        verify(bookingService).updatePendingStatus(12L, BookingStatus.CONFIRMED);
    }

    @Test
    void expiredActionTokenCannotConfirmBooking() {
        Booking booking = mock(Booking.class);
        when(bookings.findByActionTokenForUpdate("expired")).thenReturn(Optional.of(booking));
        when(booking.getActionTokenExpiresAt()).thenReturn(Instant.now().minusSeconds(1));

        OperatorBookingController controller =
                new OperatorBookingController(bookings, bookingService, pricingConversionService);
        ResponseEntity<String> response = controller.handleAction("expired", "confirm");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(bookings, never()).save(booking);
    }

    @Test
    void repeatedOperatorActionIsRejected() {
        Booking booking = mock(Booking.class);
        when(bookings.findByActionTokenForUpdate("used")).thenReturn(Optional.of(booking));
        when(booking.getActionTokenExpiresAt()).thenReturn(Instant.now().plusSeconds(600));
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);

        OperatorBookingController controller =
                new OperatorBookingController(bookings, bookingService, pricingConversionService);
        ResponseEntity<String> response = controller.handleAction("used", "confirm");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(bookings, never()).save(booking);
    }
}
