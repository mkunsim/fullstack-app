package com.example.safari.booking;

import jakarta.validation.constraints.*;
import java.util.List;

public record BookingRequest(
        @NotNull @Positive Long departureId,
        @Min(1) @Max(20) int guests,
        @NotBlank @Size(max=120) String customerName,
        @NotBlank @Email @Size(max=254) String customerEmail,
        @Size(max = 20) List<@NotNull @Positive Long> optionalExperienceIds
) {
    public BookingRequest(
            Long departureId,
            int guests,
            String customerName,
            String customerEmail
    ) {
        this(departureId, guests, customerName, customerEmail, List.of());
    }
}
