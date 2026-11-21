
package com.example.safari.booking;

import com.example.safari.operator.OperatorEmailService;
import com.example.safari.security.AppUser;
import com.example.safari.security.AppUserRepository;
import com.example.safari.security.PasswordlessAuthService;
import com.example.safari.security.Role;
import com.example.safari.tour.Departure;
import com.example.safari.tour.DepartureRepository;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository bookings;
    private final AppUserRepository users;
    private final DepartureRepository departures;
    private final OptionalExperienceRepository optionalExperiences;
    private final BookingEmailService emailService;
    private final OperatorEmailService operatorEmailService;
    private final PasswordlessAuthService authService;
    private final BookingService bookingService;

    public BookingController(
            BookingRepository bookings,
            AppUserRepository users,
            DepartureRepository departures,
            OptionalExperienceRepository optionalExperiences,
            BookingEmailService emailService,
            OperatorEmailService operatorEmailService,
            PasswordlessAuthService authService,
            BookingService bookingService
    ) {
        this.bookings = bookings;
        this.users = users;
        this.departures = departures;
        this.optionalExperiences = optionalExperiences;
        this.emailService = emailService;
        this.operatorEmailService = operatorEmailService;
        this.authService = authService;
        this.bookingService = bookingService;
    }

    public record OptionalExperienceView(
            Long experienceId,
            String name,
            String description,
            String preference,
            BigDecimal price
    ) {}

    public record BookingView(
            Long id,
            String tourName,
            String destination,
            LocalDate startDate,
            int guests,
            BigDecimal totalAmount,
            String currency,
            String status,
            String paymentStatus,
            List<OptionalExperienceView> optionalExperiences
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public BookingView create(
            @Valid @RequestBody BookingRequest request
    ) throws UnsupportedEncodingException {

        // 1. Find and lock departure

        Departure departure = departures
                .findByIdForUpdate(request.departureId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Departure not found."
                ));

        if (!departure.getTour().isActive()
                || !departure.isActive()
                || departure.getStartDate().isBefore(LocalDate.now())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Departure is not bookable."
            );
        }

        if (request.guests() > departure.getAvailableGuests()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Departure has insufficient availability."
            );
        }

        // 2. Validate selected optional-experiences

        List<Long> requestedIds = request.optionalExperienceIds() == null
                ? List.of()
                : request.optionalExperienceIds();

        Set<Long> uniqueIds = new HashSet<>(requestedIds);

        if (uniqueIds.size() != requestedIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An optional experience cannot be selected more than once."
            );
        }

        List<OptionalExperience> selectedOptionalExperiences = new ArrayList<>();

        BigDecimal optionalExperienceTotal = BigDecimal.ZERO;

        for (Long optionalExperienceId : requestedIds) {
            if (optionalExperienceId == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                "Optional experience IDs cannot be null."
                );
            }
            OptionalExperience experience = optionalExperiences.findById(optionalExperienceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Selected optional experience does not exist."
                    ));

            if (!experience.isAvailable()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Selected optional experience is unavailable."
                );
            }

            selectedOptionalExperiences.add(experience);

            optionalExperienceTotal = optionalExperienceTotal.add(experience.getPrice());
        }

        // 3. Find or create customer

        String email = request.customerEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(
                        Objects.requireNonNull(
                                AppUser.builder()
                                        .email(email)
                                        .passwordHash("PASSWORDLESS_LOGIN_DISABLED")
                                        .fullName(request.customerName().trim())
                                        .role(Role.CUSTOMER)
                                        .build()
                        )
                ));

        if (user.getRole() != Role.CUSTOMER) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email cannot be used for a customer booking."
            );
        }

        // 4. Calculate booking total

        BigDecimal total = departure.getTour().getPricePerPerson()
                .multiply(BigDecimal.valueOf(request.guests()))
                .add(optionalExperienceTotal);

        // 5. Reserve departure capacity

        departure.reserve(request.guests());

        // 6. Create booking

        Booking newBooking = Objects.requireNonNull(
                Booking.builder()
                        .customer(user)
                        .departure(departure)
                        .guests(request.guests())
                        .totalAmount(total)
                        .build()
        );

        // 7. Attach selected optional-experiences

        selectedOptionalExperiences.forEach(newBooking::addOptionalExperience);

        Booking booking = bookings.save(newBooking);

        // 8. Send notifications

        emailService.sendConfirmation(booking);
        operatorEmailService.requestAvailability(booking);

        return view(booking);
    }

    // ==========================================
    // CUSTOMER BOOKINGS
    // ==========================================

    @GetMapping("/mine")
    @Transactional
    public List<BookingView> mine(HttpServletRequest request) {
        String email = authService.customerEmailFromRequest(request);

        return bookings
                .findByCustomerEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(this::view)
                .toList();
    }

    // ==========================================
    // CANCEL BOOKING
    // ==========================================

    @PostMapping("/{id}/cancel")
    @Transactional
    public BookingView cancelOwnBooking(
            @PathVariable @NonNull Long id,
            HttpServletRequest request
    ) {
        String email = authService.customerEmailFromRequest(request);

        Booking booking = bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Booking not found."
                ));

        if (booking.getCustomer() == null
                || !booking.getCustomer().getEmail()
                .equalsIgnoreCase(email)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Booking not found."
            );
        }

        try {
            return view(bookingService.cancelBooking(id));

        } catch (IllegalStateException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage(),
                    ex
            );
        }
    }

    // ==========================================
    // BOOKING RESPONSE MAPPER
    // ==========================================

    private BookingView view(Booking booking) {

        Departure departure = booking.getDeparture();

        List<OptionalExperienceView> experiences = booking.getOptionalExperiences()
                .stream()
                .map(optionalExperience -> new OptionalExperienceView(
                        optionalExperience.getExperienceId(),
                        optionalExperience.getName(),
                        optionalExperience.getDescription(),
                        optionalExperience.getPreference(),
                        optionalExperience.getPrice()
                ))
                .toList();

        return new BookingView(
                booking.getId(),
                departure.getTour().getName(),
                departure.getTour().getDestination(),
                departure.getStartDate(),
                booking.getGuests(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getStatus().name(),
                booking.getPaymentStatus().name(),
                experiences
        );
    }
}
