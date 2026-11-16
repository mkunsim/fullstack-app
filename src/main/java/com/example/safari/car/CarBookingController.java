package com.example.safari.car;

import com.example.safari.security.AppUser;
import com.example.safari.security.AppUserRepository;
import com.example.safari.security.PasswordlessAuthService;
import com.example.safari.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestHeader;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class CarBookingController {
    private final CarRepository cars;
    private final CarBookingRepository bookings;
    private final AppUserRepository users;
    private final PasswordlessAuthService authService;
    private final CarBookingEmailService emailService;
    public CarBookingController(
            CarRepository cars,
            CarBookingRepository bookings,
            AppUserRepository users,
            PasswordlessAuthService authService,
            CarBookingEmailService emailService
    ) {
        this.cars = cars; this.bookings = bookings; this.users = users;
        this.authService = authService; this.emailService = emailService;
    }

    public record CarView(Long id, String name, String description, String destination, int passengerCapacity,
                          BigDecimal dailyRateUsd, BigDecimal guideDailyRateUsd, String imageUrl, boolean active) {}

    public record CarBookingRequest(@Positive long carId, @NotNull @FutureOrPresent LocalDate startDate,
            @NotNull @Future LocalDate endDate, @NotBlank @Size(max = 180) String pickupLocation,
            @NotBlank @Size(max = 180) String dropoffLocation, @Min(1) @Max(30) int passengers,
            boolean guideIncluded, @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Email @Size(max = 254) String customerEmail) {}

    public record CarBookingView(Long id, String carName, String destination, LocalDate startDate, LocalDate endDate,
            String pickupLocation, String dropoffLocation, int passengers, boolean driverIncluded,
            boolean guideIncluded, BigDecimal totalAmount, String currency, String status,
            String customerName, String customerEmail, String paymentStatus) {}

    public record CarInput(@NotBlank @Size(max = 140) String name, @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 180) String destination, @Min(1) @Max(30) int passengerCapacity,
            @NotNull @DecimalMin("0.01") BigDecimal dailyRateUsd,
            @NotNull @DecimalMin("0.00") BigDecimal guideDailyRateUsd,
            @NotBlank @Size(max = 1000) String imageUrl, boolean active) {}

    @GetMapping("/cars")
    public List<CarView> cars() {
        return cars.findByAvailableTrueOrderByIdAsc().stream().map(CarBookingController::toCarView).toList();
    }

    @PostMapping("/car-bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CarBookingView bookCar(@Valid @RequestBody CarBookingRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A car booking request is required.");
        }
        if (request.carId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid car ID is required.");
        }
        if (request.endDate() == null || request.startDate() == null
                || !request.endDate().isAfter(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Drop-off date must be after pick-up date.");
        }

        Car car = cars.findByIdForUpdate(request.carId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Available car not found."));
        if (!car.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Available car not found.");
        }
        if (request.passengers() > car.getPassengerCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Car does not have enough passenger capacity.");
        }

        if (bookings.countOverlaps(
                request.carId(), request.startDate(), request.endDate()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Car is already booked for these dates.");
        }

        String email = request.customerEmail().trim().toLowerCase(Locale.ROOT);
        AppUser customer = users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(
                Objects.requireNonNull(AppUser.builder()
                        .email(email)
                        .passwordHash("PASSWORDLESS_LOGIN_DISABLED")
                        .fullName(request.customerName().trim())
                        .role(Role.CUSTOMER)
                        .build())
        ));
        if (customer.getRole() != Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email cannot be used for a customer booking.");
        }

        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
        BigDecimal dailyTotal = car.getDailyRateUsd();
        if (request.guideIncluded()) {
            dailyTotal = dailyTotal.add(car.getGuideDailyRateUsd());
        }
        BigDecimal total = dailyTotal.multiply(BigDecimal.valueOf(days));

        CarBooking booking = Objects.requireNonNull(CarBooking.builder()
                .car(car)
                .customer(customer)
                .pickupDate(request.startDate())
                .dropoffDate(request.endDate())
                .passengerCount(request.passengers())
                .days(Math.toIntExact(days))
                .pickupLocation(request.pickupLocation().trim())
                .dropoffLocation(request.dropoffLocation().trim())
                .currency("USD")
                .status(CarBookingStatus.PENDING.name())
                .guideIncluded(request.guideIncluded())
                .totalAmount(total)
                .build());
        CarBooking saved = bookings.save(booking);
        emailService.sendRequestReceived(saved);
        return toBookingView(saved);
    }

    @GetMapping("/car-bookings/mine")
    @Transactional(readOnly = true)
    public List<CarBookingView> myCarBookings(HttpServletRequest request) {
        String customerEmail = authService.customerEmailFromRequest(request);
        return bookings.findByCustomerEmailOrderByCreatedAtDesc(customerEmail.trim())
                .stream()
                .map(CarBookingController::toBookingView)
                .toList();
    }

    @PostMapping("/car-bookings/{id}/cancel")
    @Transactional
    public CarBookingView cancelCarBooking(
            @PathVariable long id,
            HttpServletRequest request
    ) {
        String customerEmail = authService.customerEmailFromRequest(request);
        CarBooking booking = bookings.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car booking not found."));
        if (booking.getCustomer() == null
                || !booking.getCustomer().getEmail().equalsIgnoreCase(customerEmail)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Car booking not found.");
        }
        if (!CarBookingStatus.PENDING.name().equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending vehicle requests can be cancelled.");
        }
        booking.setStatus(CarBookingStatus.CANCELLED.name());
        CarBooking saved = bookings.save(booking);
        emailService.sendStatusUpdate(saved);
        return toBookingView(saved);
    }

    @GetMapping("/admin/cars")
    public List<CarView> allCars() {
        return cars.findAllByOrderByIdAsc().stream().map(CarBookingController::toCarView).toList();
    }

    @PostMapping("/admin/cars")
    @ResponseStatus(HttpStatus.CREATED)
    public CarView createCar(@Valid @RequestBody CarInput input) {
        Car car = new Car(
                input.name(), input.description(), input.destination(), input.passengerCapacity(),
                input.dailyRateUsd(), input.guideDailyRateUsd(), input.imageUrl(), input.active()
        );
        return toCarView(cars.save(car));
    }

    @PutMapping("/admin/cars/{id}")
    public CarView updateCar(@PathVariable long id, @Valid @RequestBody CarInput input) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid car ID is required.");
        }
        Car car = cars.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Safari car not found"));
        car.update(input.name(), car.getModel(), car.getLicensePlate(), car.getColor(), car.getCategory(),
                   input.imageUrl(), input.description(),
                   input.dailyRateUsd(), input.guideDailyRateUsd(), input.passengerCapacity(),
                   input.destination(), input.active());
        return toCarView(cars.save(car));
    }

    public record CarBookingStatusRequest(@NotNull CarBookingStatus status) {}

    @PutMapping("/admin/car-bookings/{id}/status")
    @Transactional
    public CarBookingView updateCarBookingStatus(
            @PathVariable long id,
            @Valid @RequestBody CarBookingStatusRequest request
    ) {
        CarBooking booking = bookings.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car booking not found."));
        CarBookingStatus current = parseStatus(booking.getStatus());
        CarBookingStatus next = request.status();
        boolean allowed = current == next
                || (current == CarBookingStatus.PENDING && next != CarBookingStatus.PENDING)
                || (current == CarBookingStatus.CONFIRMED && next == CarBookingStatus.CANCELLED);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This vehicle booking status transition is not allowed.");
        }
        if (current != next) {
            booking.setStatus(next.name());
            booking = bookings.save(booking);
            emailService.sendStatusUpdate(booking);
        }
        return toBookingView(booking);
    }

    @GetMapping("/admin/car-bookings")
    public List<CarBookingView> allBookings() {
        return bookings.findAll().stream().map(CarBookingController::toBookingView).toList();
    }

    private static CarView toCarView(Car c) {
        return new CarView(c.getId(), c.getMake(), c.getDescription(), c.getDestination(),
            c.getPassengerCapacity(), c.getDailyRateUsd(), c.getGuideDailyRateUsd(), c.getImageUrl(), c.isAvailable());
    }

    private CarBookingStatus parseStatus(String status) {
        try {
            return CarBookingStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unknown vehicle booking status.", ex);
        }
    }

    private static CarBookingView toBookingView(CarBooking b) {
        return new CarBookingView(b.getId(), b.getCar().getMake(), b.getCar().getDestination(),
            b.getPickupDate(), b.getDropoffDate(), b.getPickupLocation(), b.getDropoffLocation(),
            b.getPassengerCount(), true, b.isGuideIncluded(), b.getTotalAmount(), b.getCurrency(),
            b.getStatus(), b.getCustomer() == null ? null : b.getCustomer().getFullName(),
            b.getCustomer() == null ? null : b.getCustomer().getEmail(), b.getPaymentStatus().name());
    }
}