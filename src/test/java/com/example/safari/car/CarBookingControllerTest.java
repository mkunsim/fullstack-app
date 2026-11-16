package com.example.safari.car;

import com.example.safari.security.AppUser;
import com.example.safari.security.AppUserRepository;
import com.example.safari.security.PasswordlessAuthService;
import com.example.safari.security.Role;
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
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarBookingControllerTest {

    private static final long CAR_ID = 17L;
    private static final String CUSTOMER_EMAIL = "traveller@example.com";

    @Mock
    private CarRepository cars;

    @Mock
    private CarBookingRepository bookings;

    @Mock
    private AppUserRepository users;

    @Mock
    private PasswordlessAuthService authService;

    @Mock
    private CarBookingEmailService emailService;

    private CarBookingController controller;
    private Car car;
    private AppUser customer;

    @BeforeEach
    void setUp() {
        controller = new CarBookingController(cars, bookings, users, authService, emailService);
        car = Objects.requireNonNull(Car.builder()
                .id(CAR_ID)
                .make("Land Cruiser")
                .model("4x4")
                .licensePlate("ABC-123")
                .color("Green")
                .category("SUV")
                .imageUrl("https://example.com/car.jpg")
                .description("Safari vehicle")
                .dailyRateUsd(new BigDecimal("220.00"))
                .guideDailyRateUsd(new BigDecimal("75.00"))
                .passengerCapacity(6)
                .destination("Kenya")
                .available(true)
                .build());
        customer = Objects.requireNonNull(AppUser.builder()
                .email(CUSTOMER_EMAIL)
                .passwordHash("hash")
                .fullName("Test Traveller")
                .role(Role.CUSTOMER)
                .build());
    }

    @Test
    @SuppressWarnings("null")
    void createsPricedCarBookingForCustomer() {
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);
        CarBookingController.CarBookingRequest request = request(startDate, endDate);

        when(cars.findByIdForUpdate(CAR_ID)).thenReturn(Optional.of(car));
        when(bookings.countOverlaps(CAR_ID, startDate, endDate)).thenReturn(0L);
        when(users.findByEmailIgnoreCase(CUSTOMER_EMAIL)).thenReturn(Optional.of(customer));
        when(bookings.save(any(CarBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, CarBooking.class));

        CarBookingController.CarBookingView result = controller.bookCar(request);

        assertEquals("Land Cruiser", result.carName());
        assertEquals(startDate, result.startDate());
        assertEquals(endDate, result.endDate());
        assertEquals(3, result.passengers());
        assertEquals(new BigDecimal("885.00"), result.totalAmount());
        assertEquals("USD", result.currency());
        assertEquals("PENDING", result.status());
        assertEquals("Test Traveller", result.customerName());
        assertEquals(CUSTOMER_EMAIL, result.customerEmail());

        ArgumentCaptor<CarBooking> bookingCaptor = ArgumentCaptor.forClass(CarBooking.class);
        verify(bookings).save(bookingCaptor.capture());
        CarBooking saved = bookingCaptor.getValue();
        assertEquals(3, saved.getDays());
        assertEquals(new BigDecimal("295.00"), saved.getTotalAmount()
                .divide(BigDecimal.valueOf(saved.getDays())));
        assertEquals("Nairobi", saved.getPickupLocation());
        assertEquals("Serengeti", saved.getDropoffLocation());
        assertEquals(customer, saved.getCustomer());
    }

    @Test
    void rejectsCarAlreadyBookedForRequestedDates() {
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(3);
        when(cars.findByIdForUpdate(CAR_ID)).thenReturn(Optional.of(car));
        when(bookings.countOverlaps(CAR_ID, startDate, endDate)).thenReturn(1L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.bookCar(request(startDate, endDate))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(bookings).countOverlaps(CAR_ID, startDate, endDate);
        verifyNoInteractions(users);
    }

    private static CarBookingController.CarBookingRequest request(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new CarBookingController.CarBookingRequest(
                CAR_ID,
                startDate,
                endDate,
                " Nairobi ",
                "Serengeti",
                3,
                true,
                "Test Traveller",
                " Traveller@Example.com "
        );
    }
}
