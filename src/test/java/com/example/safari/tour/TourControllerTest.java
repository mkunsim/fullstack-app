package com.example.safari.tour;

import com.example.safari.booking.OptionalExperience;
import com.example.safari.booking.OptionalExperienceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TourControllerTest {

    @Test
    void includesAvailableGlobalOptionalExperiencesInTourView() {
        TourRepository tours = mock(TourRepository.class);
        OptionalExperienceRepository optionalExperiences = mock(OptionalExperienceRepository.class);
        TourController controller = new TourController(tours, optionalExperiences);
        Tour tour = Tour.builder()
                .name("Serengeti")
                .description("Safari")
                .destination("Tanzania")
                .durationDays(4)
                .pricePerPerson(new BigDecimal("125.00"))
                .imageUrl("https://example.com/tour.jpg")
                .build();
        OptionalExperience optionalExperience = new OptionalExperience(
                "Local hospitality",
                "A local cultural experience.",
                "Share any requirements in advance.",
                new BigDecimal("25.50"),
                true
        );
        optionalExperience.setId(41L);

        when(optionalExperiences.findByAvailableTrueOrderByIdAsc()).thenReturn(List.of(optionalExperience));
        when(tours.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(tour));

        TourController.TourView result = controller.all().get(0);

        assertEquals(1, result.optionalExperiences().size());
        assertEquals(41L, result.optionalExperiences().get(0).id());
        assertEquals("Local hospitality", result.optionalExperiences().get(0).name());
        assertEquals("Share any requirements in advance.", result.optionalExperiences().get(0).serviceRequirements());
        assertEquals(new BigDecimal("25.50"), result.optionalExperiences().get(0).price());
    }
}
