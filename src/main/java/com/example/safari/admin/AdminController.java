
package com.example.safari.admin;

import com.example.safari.booking.AddOn;
import com.example.safari.booking.AddOnRepository;
import com.example.safari.booking.Booking;
import com.example.safari.booking.BookingRepository;
import com.example.safari.booking.BookingService;
import com.example.safari.booking.BookingController;
import com.example.safari.booking.BookingStatus;
import com.example.safari.tour.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TourRepository tours;
    private final DepartureRepository departures;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final AddOnRepository addons;

    public AdminController(
            TourRepository tours,
            DepartureRepository departures,
            BookingRepository bookings,
            BookingService bookingService,
            AddOnRepository addons
    ) {
        this.tours = tours;
        this.departures = departures;
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.addons = addons;
    }

    // ==========================================
    // REQUEST DTOs
    // ==========================================

    public record TourInput(
            @NotBlank @Size(max = 140) String name,
            @NotBlank @Size(max = 3000) String description,
            @NotBlank @Size(max = 180) String destination,
            @Min(1) @Max(60) int durationDays,
            @NotNull @DecimalMin("0.01") BigDecimal pricePerPerson,
            @NotBlank @Size(max = 1000) String imageUrl,
            boolean active
    ) {}

    public record AddOnSelection(
            boolean selected
    ) {}

    public record AddOnInput(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 2000) String preference,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            boolean available
    ) {}

    public record DepartureInput(
            @NotNull @FutureOrPresent LocalDate startDate,
            @Min(1) @Max(100) int capacity
    ) {}

    public record BookingAdminView(
            Long id,
            String customerName,
            String customerEmail,
            String tourName,
            String destination,
            LocalDate startDate,
            int guests,
            BigDecimal totalAmount,
            String currency,
            String status,
            String paymentStatus,
            List<BookingController.AddOnView> addons
    ) {}

    public record BookingStatusRequest(
            @NotNull BookingStatus status
    ) {}

    // ==========================================
    // TOURS
    // ==========================================

    @GetMapping("/tours")
    public List<Tour> allTours() {
        return tours.findAllByOrderByIdAsc();
    }

    @PostMapping("/tours")
    @ResponseStatus(HttpStatus.CREATED)
    public Tour createTour(@Valid @RequestBody TourInput request) {

        Tour tour = Objects.requireNonNull(
                Tour.builder()
                        .name(request.name())
                        .description(request.description())
                        .destination(request.destination())
                        .durationDays(request.durationDays())
                        .pricePerPerson(request.pricePerPerson())
                        .imageUrl(request.imageUrl())
                        .build()
        );

        return tours.save(tour);
    }

    @PutMapping("/tours/{id}")
    public Tour updateTour(
            @PathVariable Long id,
            @Valid @RequestBody TourInput request
    ) {

        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid tour ID is required."
            );
        }

        Tour tour = tours.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Tour not found."
                ));

        tour.update(
                request.name(),
                request.description(),
                request.destination(),
                request.durationDays(),
                request.pricePerPerson(),
                request.imageUrl(),
                request.active()
        );

        return tours.save(tour);
    }

    // ==========================================
    // GLOBAL ADD-ON MANAGEMENT
    // ==========================================

    @GetMapping("/addons")
    @Transactional(readOnly = true)
    public List<AddOn> allAddons() {
        return addons.findAllByOrderByIdAsc();
    }

    @PostMapping("/addons")
    @ResponseStatus(HttpStatus.CREATED)
    public AddOn createAddOn(
            @Valid @RequestBody AddOnInput request
    ) {

        AddOn addon = Objects.requireNonNull(
                AddOn.builder()
                        .name(request.name())
                        .description(request.description())
                        .preference(request.preference())
                        .price(request.price())
                        .available(request.available())
                        .build()
        );

        return addons.save(addon);
    }

    @PutMapping("/addons/{id}")
    public AddOn updateAddOn(
            @PathVariable @NonNull Long id,
            @Valid @RequestBody AddOnInput request
    ) {

        AddOn addon = addons.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Extra not found."
                ));

        addon.setName(request.name());
        addon.setDescription(request.description());
        addon.setPreference(request.preference());
        addon.setPrice(request.price());
        addon.setAvailable(request.available());

        return addons.save(addon);
    }

    @PutMapping("/addons/{id}/availability")
    public AddOn updateAddOnAvailability(
            @PathVariable @NonNull Long id,
            @Valid @RequestBody AddOnSelection request
    ) {

        AddOn addon = addons.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Extra not found."
                ));

        addon.setAvailable(request.selected());

        return addons.save(addon);
    }

    // ==========================================
    // BOOKINGS
    // ==========================================

    @GetMapping("/bookings")
    @Transactional(readOnly = true)
    public List<BookingAdminView> allBookings() {

        return bookings.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::bookingView)
                .toList();
    }

    @PutMapping("/bookings/{id}/status")
    @Transactional
    public BookingAdminView updateBookingStatus(
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusRequest request
    ) {

        if (request.status() == BookingStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a final booking status."
            );
        }

        try {

            Booking booking = bookingService.updatePendingStatus(
                    id,
                    request.status()
            );

            return bookingView(booking);

        } catch (IllegalArgumentException ex) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Booking not found.",
                    ex
            );

        } catch (IllegalStateException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    ex.getMessage(),
                    ex
            );
        }
    }

    // ==========================================
    // DEPARTURES
    // ==========================================

    @PostMapping("/tours/{id}/departures")
    @ResponseStatus(HttpStatus.CREATED)
    public Departure createDeparture(
            @PathVariable Long id,
            @Valid @RequestBody DepartureInput request
    ) {

        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid tour ID is required."
            );
        }

        Tour tour = tours.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Tour not found."
                ));

        return departures.save(
                Objects.requireNonNull(
                        Departure.builder()
                                .tour(tour)
                                .startDate(request.startDate())
                                .capacity(request.capacity())
                                .build()
                )
        );
    }

    // ==========================================
    // BOOKING RESPONSE MAPPER
    // ==========================================

    private BookingAdminView bookingView(Booking booking) {

        return new BookingAdminView(
                booking.getId(),
                booking.getCustomer().getFullName(),
                booking.getCustomer().getEmail(),
                booking.getDeparture().getTour().getName(),
                booking.getDeparture().getTour().getDestination(),
                booking.getDeparture().getStartDate(),
                booking.getGuests(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getStatus().name(),
                booking.getPaymentStatus().name(),
                booking.getAddons().stream()
                        .map(addon -> new BookingController.AddOnView(
                                addon.getId(),
                                addon.getName(),
                                addon.getDescription(),
                                addon.getPreference(),
                                addon.getPrice()
                        ))
                        .toList()
        );
    }
}
