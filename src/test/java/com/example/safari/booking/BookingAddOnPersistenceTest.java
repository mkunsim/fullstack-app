package com.example.safari.booking;

import com.example.safari.security.AppUser;
import com.example.safari.security.Role;
import com.example.safari.tour.Departure;
import com.example.safari.tour.Tour;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BookingAddOnPersistenceTest {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BookingRepository bookings;

    @Test
    void persistsBookingAddOnRelationshipOnBooking() {
        Tour tour = new Tour(
                "Serengeti",
                "Safari",
                "Tanzania",
                4,
                new BigDecimal("125.00"),
                "https://example.com/tour.jpg"
        );
        entityManager.persist(tour);

        Departure departure = new Departure(
                tour,
                LocalDate.now().plusDays(30),
                10
        );
        entityManager.persist(departure);

        AppUser customer = new AppUser(
                "traveller@example.com",
                "hash",
                "Test Traveller",
                Role.CUSTOMER
        );
        entityManager.persist(customer);

        AddOn selectedAddOn = new AddOn(
                "Local hospitality",
                "A local cultural experience.",
                "Share any requirements in advance.",
                new BigDecimal("25.50"),
                true
        );
        entityManager.persist(selectedAddOn);
        entityManager.flush();

        Booking booking = new Booking(
                customer,
                departure,
                2,
                new BigDecimal("275.50")
        );
        booking.addAddOn(selectedAddOn);
        entityManager.persist(booking);
        entityManager.flush();
        Long bookingId = Objects.requireNonNull(booking.getId());
        entityManager.clear();

        Booking persistedBooking = bookings.findById(bookingId).orElseThrow();

        assertEquals(1, persistedBooking.getAddons().size());
        assertEquals(selectedAddOn.getId(), persistedBooking.getAddons().get(0).getId());
        assertEquals("Local hospitality", persistedBooking.getAddons().get(0).getName());
        assertEquals(new BigDecimal("25.50"), persistedBooking.getAddons().get(0).getPrice());
    }
}
