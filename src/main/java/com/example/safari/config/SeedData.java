
package com.example.safari.config;

import com.example.safari.security.AppUserRepository;
import com.example.safari.booking.AddOn;
import com.example.safari.booking.AddOnRepository;
import com.example.safari.tour.*;
import com.example.safari.car.Car;
import com.example.safari.car.CarRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Configuration
public class SeedData {

    @Value("${app.admin.initial-email:}")
    private String initialAdminEmail;

    @Value("${app.admin.initial-name:Administrator}")
    private String initialAdminName;

    @Bean
    CommandLineRunner seed(
            TourRepository tours,
            DepartureRepository departures,
            AppUserRepository users,
            CarRepository cars,
            AddOnRepository addons
    ) {
        return args -> {

            if (initialAdminEmail != null && !initialAdminEmail.isBlank()) {
                String email = initialAdminEmail.trim().toLowerCase(java.util.Locale.ROOT);
                var existingAdmin = users.findByEmailIgnoreCase(email);
                if (existingAdmin.isPresent() && existingAdmin.get().getRole()
                        != com.example.safari.security.Role.ADMIN) {
                    throw new IllegalStateException(
                            "Configured initial admin email belongs to a non-admin account.");
                }
                if (existingAdmin.isEmpty()) {
                    users.save(Objects.requireNonNull(
                            com.example.safari.security.AppUser.builder()
                                    .email(email)
                                    .passwordHash("PASSWORDLESS_LOGIN_DISABLED")
                                    .fullName(initialAdminName == null || initialAdminName.isBlank()
                                            ? "Administrator" : initialAdminName.trim())
                                    .role(com.example.safari.security.Role.ADMIN)
                                    .build()));
                }
            }

            if (addons.count() == 0) {
                addons.saveAll(Objects.requireNonNull(List.of(
                        Objects.requireNonNull(AddOn.builder()
                                .name("Meet a local person")
                                .description("Meet and learn from a local person in the destination community.")
                                .preference("Specify whether you would prefer to meet a woman or man, and arrange the meeting with the local host in advance.")
                                .price(new BigDecimal("20.00"))
                                .available(true)
                                .build()),
                        Objects.requireNonNull(AddOn.builder()
                                .name("Local hospitality experience")
                                .description("Enjoy a welcome and hospitality experience with local hosts.")
                                .preference("Share dietary or accessibility requirements when booking.")
                                .price(new BigDecimal("30.00"))
                                .available(true)
                                .build()),
                        Objects.requireNonNull(AddOn.builder()
                                .name("Local activity companion")
                                .description("Arrange a local host to accompany you during a planned cultural activity.")
                                .preference("Agree on the activity and schedule with the operator in advance.")
                                .price(new BigDecimal("50.00"))
                                .available(true)
                                .build()),
                        Objects.requireNonNull(AddOn.builder()
                                .name("Guided cultural exchange")
                                .description("Take part in a guided cultural exchange with a local community.")
                                .preference("The activity and community participation must be confirmed by the operator before the booking is finalized.")
                                .price(new BigDecimal("40.00"))
                                .available(true)
                                .build()),
                        Objects.requireNonNull(AddOn.builder()
                                .name("Customized cultural experience")
                                .description("Request a personalized activity tailored to your interests.")
                                .preference("The activity, schedule, and itinerary must be confirmed by the operator or community before the booking is finalized.")
                                .price(new BigDecimal("75.00"))
                                .available(true)
                                .build())
                )));
            }

            // ==========================================
            // 1. SEED SAFARI VEHICLES
            // ==========================================

            if (cars.count() == 0) {
                cars.save(Objects.requireNonNull(Car.builder()
                        .make("Land Cruiser")
                        .model("4×4 Safari Vehicle")
                        .licensePlate("ABC-123")
                        .color("Green")
                        .category("Tanzania & Kenya")
                        .imageUrl("https://images.unsplash.com/photo-1516426122078-c23e76319801?auto=format&fit=crop&w=1200&q=80")
                        .description("Pop-up roof, air conditioning and room for luggage. Professional driver included.")
                        .dailyRateUsd(new BigDecimal("220.00"))
                        .guideDailyRateUsd(new BigDecimal("75.00"))
                        .passengerCapacity(6)
                        .destination("Tanzania & Kenya")
                        .available(true)
                        .build()));

                cars.save(Objects.requireNonNull(Car.builder()
                        .make("Safari Minivan")
                        .model("Comfortable group transport")
                        .licensePlate("DEF-456")
                        .color("White")
                        .category("Kenya")
                        .imageUrl("https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=80")
                        .description("Comfortable group transport for parks and airport transfers. Professional driver included.")
                        .dailyRateUsd(new BigDecimal("145.00"))
                        .guideDailyRateUsd(new BigDecimal("60.00"))
                        .passengerCapacity(7)
                        .destination("Kenya")
                        .available(true)
                        .build()));

                cars.save(Objects.requireNonNull(Car.builder()
                        .make("Luxury")
                        .model("4×4 Safari Vehicle")
                        .licensePlate("GHI-789")
                        .color("Black")
                        .category("East Africa")
                        .imageUrl("https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=1200&q=80")
                        .description("Premium 4×4 safari vehicle with spacious seating and a professional driver.")
                        .dailyRateUsd(new BigDecimal("310.00"))
                        .guideDailyRateUsd(new BigDecimal("90.00"))
                        .passengerCapacity(4)
                        .destination("East Africa")
                        .available(true)
                        .build()));
            }

            // ==========================================
            // 2. SEED SAFARI TOURS
            // ==========================================

            if (tours.count() == 0) {
                Tour serengeti = tours.save(
                        Objects.requireNonNull(Tour.builder()
                                .name("Serengeti Great Migration")
                                .description("Follow the great herds across open plains with expert local guides.")
                                .destination("Serengeti, Tanzania")
                                .durationDays(4)
                                .pricePerPerson(new BigDecimal("1890.00"))
                                .imageUrl("https://images.unsplash.com/photo-1516426122078-c23e76319801?auto=format&fit=crop&w=1200&q=80")
                                .build())
                );
                Tour amboseli = tours.save(
                        Objects.requireNonNull(Tour.builder()
                                .name("Amboseli Elephant Trails")
                                .description("See elephant herds against the spectacular backdrop of Mount Kilimanjaro.")
                                .destination("Amboseli, Kenya")
                                .durationDays(3)
                                .pricePerPerson(new BigDecimal("990.00"))
                                .imageUrl("https://images.unsplash.com/photo-1551632811-561732d1e306?auto=format&fit=crop&w=1200&q=80")
                                .build())
                );
                Tour gorilla = tours.save(
                        Objects.requireNonNull(Tour.builder()
                                .name("Bwindi Forest Discovery")
                                .description("A guided forest adventure in Uganda's extraordinary biodiversity hotspot.")
                                .destination("Bwindi, Uganda")
                                .durationDays(3)
                                .pricePerPerson(new BigDecimal("1490.00"))
                                .imageUrl("https://images.unsplash.com/photo-1549366021-9f761d450615?auto=format&fit=crop&w=1200&q=80")
                                .build())
                );

                // Initial departure schedules
                departures.save(Objects.requireNonNull(Departure.builder()
                        .tour(serengeti)
                        .startDate(LocalDate.now().plusDays(30))
                        .capacity(12)
                        .build()));
                departures.save(Objects.requireNonNull(Departure.builder()
                        .tour(serengeti)
                        .startDate(LocalDate.now().plusDays(60))
                        .capacity(12)
                        .build()));
                departures.save(Objects.requireNonNull(Departure.builder()
                        .tour(amboseli)
                        .startDate(LocalDate.now().plusDays(20))
                        .capacity(10)
                        .build()));
                departures.save(Objects.requireNonNull(Departure.builder()
                        .tour(gorilla)
                        .startDate(LocalDate.now().plusDays(45))
                        .capacity(8)
                        .build()));
            }

            // ==========================================
            // 3. REPAIR MISSING DEPARTURES
            // ==========================================

            seedMissingDepartures(tours, departures);

        };
    }

    // ==============================================
    // 4. ENSURE EVERY ACTIVE TOUR HAS A DEPARTURE
    // ==============================================

    private static void seedMissingDepartures(
            TourRepository tours,
            DepartureRepository departures
    ) {

        List<Tour> activeTours = tours.findByActiveTrueOrderByIdAsc();

        for (Tour tour : activeTours) {

            boolean hasActiveDepartures =
                    !departures.findActiveForTour(tour.getId()).isEmpty();

            if (!hasActiveDepartures) {

                Departure departure = Objects.requireNonNull(
                        Departure.builder()
                        .tour(tour)
                        .startDate(LocalDate.now().plusDays(30))
                        .capacity(10)
                        .build() );

                departures.save(departure);

            }
        }
    }
}
