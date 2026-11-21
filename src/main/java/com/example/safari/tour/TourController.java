package com.example.safari.tour;

import com.example.safari.booking.AddOn;
import com.example.safari.booking.AddOnRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/tours")
public class TourController {
    private final TourRepository tours;
    private final AddOnRepository addons;

    public TourController(TourRepository tours, AddOnRepository addons) {
        this.tours = tours;
        this.addons = addons;
    }

    public record AddOnView(
            Long id,
            String name,
            String description,
            String serviceRequirements,
            BigDecimal price,
            boolean available
    ) {}
    public record TourView(
            Long id,
            String name,
            String description,
            String destination,
            int durationDays,
            BigDecimal pricePerPerson,
            String imageUrl,
            boolean active,
            List<Departure> departures,
            List<AddOnView> addons
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<TourView> all() {
        List<AddOn> availableAddons = addons.findByAvailableTrueOrderByIdAsc();
        return tours.findByActiveTrueOrderByIdAsc().stream()
                .map(tour -> view(tour, availableAddons))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public TourView one(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid tour ID is required.");
        }
        List<AddOn> availableAddons = addons.findByAvailableTrueOrderByIdAsc();
        return tours.findById(id).filter(tour -> tour.isActive())
            .map(tour -> view(tour, availableAddons))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tour not found"));
    }

    private TourView view(Tour tour, List<AddOn> availableAddons) {
        return new TourView(
                tour.getId(),
                tour.getName(),
                tour.getDescription(),
                tour.getDestination(),
                tour.getDurationDays(),
                tour.getPricePerPerson(),
                tour.getImageUrl(),
                tour.isActive(),
                tour.getDepartures(),
                availableAddons.stream()
                        .map(addon -> new AddOnView(
                                addon.getId(),
                                addon.getName(),
                                addon.getDescription(),
                                addon.getPreference(),
                                addon.getPrice(),
                                addon.isAvailable()
                        ))
                        .toList()
        );
    }
}
