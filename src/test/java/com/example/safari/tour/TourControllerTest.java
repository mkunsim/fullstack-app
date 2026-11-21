package com.example.safari.tour;

import com.example.safari.booking.AddOn;
import com.example.safari.booking.AddOnRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TourControllerTest {

    @Test
    void includesAvailableGlobalAddonsInTourView() {
        TourRepository tours = mock(TourRepository.class);
        AddOnRepository addons = mock(AddOnRepository.class);
        TourController controller = new TourController(tours, addons);
        Tour tour = Tour.builder()
                .name("Serengeti")
                .description("Safari")
                .destination("Tanzania")
                .durationDays(4)
                .pricePerPerson(new BigDecimal("125.00"))
                .imageUrl("https://example.com/tour.jpg")
                .build();
        AddOn addon = new AddOn(
                "Local hospitality",
                "A local cultural experience.",
                "Share any requirements in advance.",
                new BigDecimal("25.50"),
                true
        );
        addon.setId(41L);

        when(addons.findByAvailableTrueOrderByIdAsc()).thenReturn(List.of(addon));
        when(tours.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(tour));

        TourController.TourView result = controller.all().get(0);

        assertEquals(1, result.addons().size());
        assertEquals(41L, result.addons().get(0).id());
        assertEquals("Local hospitality", result.addons().get(0).name());
        assertEquals("Share any requirements in advance.", result.addons().get(0).serviceRequirements());
        assertEquals(new BigDecimal("25.50"), result.addons().get(0).price());
    }
}
