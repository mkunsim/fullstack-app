
package com.example.safari.booking;

import com.example.safari.operator.OperatorEmailService;
import com.example.safari.security.*;
import com.example.safari.tour.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    BookingRepository bookings;

    @Mock
    AppUserRepository users;

    @Mock
    DepartureRepository departures;

    @Mock
    AddOnRepository addOns;

    @Mock
    BookingEmailService emailService;

    @Mock
    OperatorEmailService operatorEmailService;

    @Mock
    PasswordlessAuthService authService;

    @Mock
    BookingService bookingService;

    private BookingController controller;
    private Tour tour;
    private Departure departure;
    private AppUser customer;

    @BeforeEach
    void setUp() {

        controller = new BookingController(
                bookings,
                users,
                departures,
                addOns,
                emailService,
                operatorEmailService,
                authService,
                bookingService
        );

        tour = Tour.builder()
                .name("Serengeti")
                .description("Safari")
                .destination("Tanzania")
                .durationDays(4)
                .pricePerPerson(new BigDecimal("125.00"))
                .imageUrl("https://example.com/tour.jpg")
                .build();

        departure = Departure.builder()
                .tour(tour)
                .startDate(LocalDate.now().plusDays(30))
                .capacity(10)
                .reservedGuests(0)
                .build();

        customer = AppUser.builder()
                .email("traveller@example.com")
                .passwordHash("hash")
                .fullName("Test Traveller")
                .role(Role.CUSTOMER)
                .build();
    }

    // ==========================================
    // SUCCESSFUL BOOKING
    // ==========================================

    @Test
    void customerSubmissionCreatesPendingUnpaidBookingAndNotifiesBothParties()
            throws Exception {

        when(departures.findByIdForUpdate(7L))
                .thenReturn(Optional.of(departure));

        when(users.findByEmailIgnoreCase("traveller@example.com"))
                .thenReturn(Optional.of(customer));

        when(bookings.save(any(Booking.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0, Booking.class)
                );

        BookingController.BookingView result = controller.create(
                new BookingRequest(
                        7L,
                        2,
                        "Test Traveller",
                        " Traveller@Example.com "
                )
        );

        assertEquals("PENDING", result.status());
        assertEquals("UNPAID", result.paymentStatus());
        assertEquals(new BigDecimal("250.00"), result.totalAmount());
        assertEquals("USD", result.currency());
        assertEquals(2, departure.getReservedGuests());

        ArgumentCaptor<Booking> captor =
                ArgumentCaptor.forClass(Booking.class);

        verify(bookings).save(captor.capture());

        Booking savedBooking = captor.getValue();

        assertEquals(BookingStatus.PENDING, savedBooking.getStatus());

        assertEquals(
                com.example.safari.payment.PaymentStatus.UNPAID,
                savedBooking.getPaymentStatus()
        );

        verify(emailService).sendConfirmation(savedBooking);
        verify(operatorEmailService).requestAvailability(savedBooking);
    }

    // ==========================================
    // CUSTOMER ROLE VALIDATION
    // ==========================================

    @Test
    void customerCannotBookUsingOperatorOrAdminAccount() {

        AppUser admin = AppUser.builder()
                .email("traveller@example.com")
                .passwordHash("hash")
                .fullName("Admin")
                .role(Role.ADMIN)
                .build();

        when(departures.findByIdForUpdate(7L))
                .thenReturn(Optional.of(departure));

        when(users.findByEmailIgnoreCase("traveller@example.com"))
                .thenReturn(Optional.of(admin));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.create(
                        new BookingRequest(
                                7L,
                                1,
                                "Admin",
                                "traveller@example.com"
                        )
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verifyNoInteractions(bookings);
    }

    // ==========================================
    // DEPARTURE VALIDATION
    // ==========================================

    @Test
    void rejectsUnavailableDeparture() {

        when(departures.findByIdForUpdate(7L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.create(
                        new BookingRequest(
                                7L,
                                1,
                                "Test Traveller",
                                "traveller@example.com"
                        )
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(bookings);
    }

    @Test
    void rejectsInsufficientSeats() {

        Departure almostFull = Departure.builder()
                .tour(tour)
                .startDate(LocalDate.now().plusDays(30))
                .capacity(1)
                .reservedGuests(0)
                .build();

        when(departures.findByIdForUpdate(7L))
                .thenReturn(Optional.of(almostFull));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.create(
                        new BookingRequest(
                                7L,
                                2,
                                "Test Traveller",
                                "traveller@example.com"
                        )
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verifyNoInteractions(bookings);
    }

    // ==========================================
    // GLOBAL ADD-ON SELECTION
    // ==========================================

    @Test
    void selectedAvailableAddOnAddsOneTimePriceToBooking()
            throws Exception {

        AddOn option = new AddOn(
                "Local hospitality",
                "Share a traditional meal with a local family.",
                "Confirm dietary needs in advance.",
                new BigDecimal("25.50"),
                true
        );

        option.setId(41L);

        when(departures.findByIdForUpdate(7L))
                .thenReturn(Optional.of(departure));

        when(addOns.findById(41L))
                .thenReturn(Optional.of(option));

        when(users.findByEmailIgnoreCase("traveller@example.com"))
                .thenReturn(Optional.of(customer));

        when(bookings.save(any(Booking.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0, Booking.class)
                );

        BookingController.BookingView result = controller.create(
                new BookingRequest(
                        7L,
                        2,
                        "Test Traveller",
                        "traveller@example.com",
                        List.of(41L)
                )
        );

        assertEquals(
                new BigDecimal("275.50"),
                result.totalAmount()
        );

        assertEquals(1, result.addOns().size());

        assertEquals(
                "Local hospitality",
                result.addOns().get(0).name()
        );

        assertEquals(
                new BigDecimal("25.50"),
                result.addOns().get(0).price()
        );

        ArgumentCaptor<Booking> captor =
                ArgumentCaptor.forClass(Booking.class);

        verify(bookings).save(captor.capture());

        Booking savedBooking = captor.getValue();

        assertEquals(1, savedBooking.getAddOns().size());

        assertEquals(
                "Local hospitality",
                savedBooking.getAddOns().get(0).getName()
        );

        verify(addOns).findById(41L);
    }

    // ==========================================
    // UNAVAILABLE ADD-ON
    // ==========================================

    @Test
    void rejectsUnavailableAddOn() {

        AddOn option = new AddOn(
                "Female tour guide",
                "A guided experience.",
                "Subject to guide availability.",
                new BigDecimal("10.00"),
                false
        );

        option.setId(42L);

        when(departures.findByIdForUpdate(7L))
                .thenReturn(Optional.of(departure));

        when(addOns.findById(42L))
                .thenReturn(Optional.of(option));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.create(
                        new BookingRequest(
                                7L,
                                1,
                                "Test Traveller",
                                "traveller@example.com",
                                List.of(42L)
                        )
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(users, bookings);
    }

    // ==========================================
    // NON-EXISTENT ADD-ON
    // ==========================================

    @Test
    void rejectsUnknownAddOn() {

        when(departures.findByIdForUpdate(7L))
                .thenReturn(Optional.of(departure));

        when(addOns.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.create(
                        new BookingRequest(
                                7L,
                                1,
                                "Test Traveller",
                                "traveller@example.com",
                                List.of(99L)
                        )
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(users, bookings);
    }

    // ==========================================
    // DUPLICATE ADD-ON SELECTION
    // ==========================================

    @Test
    void rejectsDuplicateAddOnSelection() {

        when(departures.findByIdForUpdate(7L))
                .thenReturn(Optional.of(departure));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.create(
                        new BookingRequest(
                                7L,
                                1,
                                "Test Traveller",
                                "traveller@example.com",
                                List.of(43L, 43L)
                        )
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(addOns, users, bookings);
    }
}
