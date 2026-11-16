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
    void includesAvailableGlobalAddOnsInTourView() {
        TourRepository tours = mock(TourRepository.class);
        AddOnRepository addOns = mock(AddOnRepository.class);
        TourController controller = new TourController(tours, addOns);
        Tour tour = Tour.builder()
                .name("Serengeti")
                .description("Safari")
                .destination("Tanzania")
                .durationDays(4)
                .pricePerPerson(new BigDecimal("125.00"))
                .imageUrl("https://example.com/tour.jpg")
                .build();
        AddOn addOn = new AddOn(
                "Local hospitality",
                "A local cultural experience.",
                "Share any requirements in advance.",
                new BigDecimal("25.50"),
                true
        );
        addOn.setId(41L);

        when(addOns.findByAvailableTrueOrderByIdAsc()).thenReturn(List.of(addOn));
        when(tours.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(tour));

        TourController.TourView result = controller.all().get(0);

        assertEquals(1, result.addOns().size());
        assertEquals(41L, result.addOns().get(0).id());
        assertEquals("Local hospitality", result.addOns().get(0).name());
        assertEquals("Share any requirements in advance.", result.addOns().get(0).serviceRequirements());
        assertEquals(new BigDecimal("25.50"), result.addOns().get(0).price());
    }
}
