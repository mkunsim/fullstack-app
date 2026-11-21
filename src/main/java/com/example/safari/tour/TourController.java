package com.example.safari.tour;

import com.example.safari.booking.OptionalExperience;
import com.example.safari.booking.OptionalExperienceRepository;
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
    private final OptionalExperienceRepository optionalExperiences;

    public TourController(TourRepository tours, OptionalExperienceRepository optionalExperiences) {
        this.tours = tours;
        this.optionalExperiences = optionalExperiences;
    }

    public record OptionalExperienceView(
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
            List<OptionalExperienceView> optionalExperiences
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<TourView> all() {
        List<OptionalExperience> availableOptionalExperiences = optionalExperiences.findByAvailableTrueOrderByIdAsc();
        return tours.findByActiveTrueOrderByIdAsc().stream()
                .map(tour -> view(tour, availableOptionalExperiences))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public TourView one(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid tour ID is required.");
        }
        List<OptionalExperience> availableOptionalExperiences = optionalExperiences.findByAvailableTrueOrderByIdAsc();
        return tours.findById(id).filter(tour -> tour.isActive())
            .map(tour -> view(tour, availableOptionalExperiences))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tour not found"));
    }

    private TourView view(Tour tour, List<OptionalExperience> availableOptionalExperiences) {
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
                availableOptionalExperiences.stream()
                        .map(optionalExperience -> new OptionalExperienceView(
                                optionalExperience.getId(),
                                optionalExperience.getName(),
                                optionalExperience.getDescription(),
                                optionalExperience.getPreference(),
                                optionalExperience.getPrice(),
                                optionalExperience.isAvailable()
                        ))
                        .toList()
        );
    }
}
