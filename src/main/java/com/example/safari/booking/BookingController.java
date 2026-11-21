
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
    private final AddOnRepository addons;
    private final BookingEmailService emailService;
    private final OperatorEmailService operatorEmailService;
    private final PasswordlessAuthService authService;
    private final BookingService bookingService;

    public BookingController(
            BookingRepository bookings,
            AppUserRepository users,
            DepartureRepository departures,
            AddOnRepository addons,
            BookingEmailService emailService,
            OperatorEmailService operatorEmailService,
            PasswordlessAuthService authService,
            BookingService bookingService
    ) {
        this.bookings = bookings;
        this.users = users;
        this.departures = departures;
        this.addons = addons;
        this.emailService = emailService;
        this.operatorEmailService = operatorEmailService;
        this.authService = authService;
        this.bookingService = bookingService;
    }

    public record AddOnView(
            Long addonId,
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
            List<AddOnView> addons
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

        // 2. Validate selected addons

        List<Long> requestedIds = request.addonIds() == null
                ? List.of()
                : request.addonIds();

        Set<Long> uniqueIds = new HashSet<>(requestedIds);

        if (uniqueIds.size() != requestedIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An extra cannot be selected more than once."
            );
        }

        List<AddOn> selectedAddons = new ArrayList<>();

        BigDecimal addonTotal = BigDecimal.ZERO;

        for (Long addonId : requestedIds) {
            if (addonId == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                "Extra IDs cannot be null."
                );
            }
            AddOn addon = addons.findById(addonId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Selected extra does not exist."
                    ));

            if (!addon.isAvailable()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Selected extra is unavailable."
                );
            }

            selectedAddons.add(addon);

            addonTotal = addonTotal.add(addon.getPrice());
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
                .add(addonTotal);

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

        // 7. Attach selected addons

        selectedAddons.forEach(newBooking::addAddOn);

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

        List<AddOnView> addons = booking.getAddons()
                .stream()
                .map(addon -> new AddOnView(
                        addon.getId(),
                        addon.getName(),
                        addon.getDescription(),
                        addon.getPreference(),
                        addon.getPrice()
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
                addons
        );
    }
}
