package com.example.safari.tour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TourSerializationTest {

    @Test
    void serializesTourDeparturesWithoutRecursivelySerializingTour() throws Exception {
        Tour tour = Objects.requireNonNull(Tour.builder()
                .id(1L)
                .name("Savannah Explorer")
                .description("A guided safari")
                .destination("Kenya")
                .durationDays(3)
                .pricePerPerson(new BigDecimal("990.00"))
                .imageUrl("https://example.com/safari.jpg")
                .build());
        Departure departure = Objects.requireNonNull(Departure.builder()
                .id(2L)
                .tour(tour)
                .startDate(LocalDate.of(2027, 1, 15))
                .capacity(10)
                .build());
        tour.setDepartures(List.of(departure));

        JsonNode json = JsonMapper.builder()
                .findAndAddModules()
                .build()
                .valueToTree(tour);

        assertEquals("Savannah Explorer", json.get("name").asText());
        assertEquals(2027, json.get("departures").get(0).get("startDate").get(0).asInt());
        assertEquals(10, json.get("departures").get(0).get("capacity").asInt());
        assertFalse(json.get("departures").get(0).has("tour"));
    }
}
