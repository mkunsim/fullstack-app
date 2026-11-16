package com.example.safari.car;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Data
@Table(name = "cars")
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String make;

    @Column(length = 100)
    private String model;

    @Column(length = 20)
    private String licensePlate;

    @Column(length = 30)
    private String color;

    @Column(length = 50)
    private String category;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private BigDecimal dailyRateUsd;

    @Column(nullable = false)
    private BigDecimal guideDailyRateUsd;

    @Column(nullable = false)
    private int passengerCapacity;

    @Column(nullable = false)
    private String destination;

    @Builder.Default
    @Column(nullable = false)
    private boolean available = true;

    public Car(String make, String model, String licensePlate, String color, String category,
               String imageUrl, String description, BigDecimal dailyRateUsd, BigDecimal guideDailyRateUsd,
               int passengerCapacity, String destination, boolean available) {
        this.make = make;
        this.model = model;
        this.licensePlate = licensePlate;
        this.color = color;
        this.category = category;
        this.imageUrl = imageUrl;
        this.description = description;
        this.dailyRateUsd = dailyRateUsd;
        this.guideDailyRateUsd = guideDailyRateUsd;
        this.passengerCapacity = passengerCapacity;
        this.destination = destination;
        this.available = available;
    }

    // Convenience constructor used by the admin car form.
    public Car(String name, String description, String destination, int passengerCapacity,
               BigDecimal dailyRateUsd, BigDecimal guideDailyRateUsd, String imageUrl, boolean available) {
        this(name, name, "", "", "SUV", imageUrl, description, dailyRateUsd, guideDailyRateUsd,
                passengerCapacity, destination, available);
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void update(String make, String model, String licensePlate, String color, String category,
                       String imageUrl, String description, BigDecimal dailyRateUsd, BigDecimal guideDailyRateUsd,
                       int passengerCapacity, String destination, boolean available) {
        this.make = make;
        this.model = model;
        this.licensePlate = licensePlate;
        this.color = color;
        this.category = category;
        this.imageUrl = imageUrl;
        this.description = description;
        this.dailyRateUsd = dailyRateUsd;
        this.guideDailyRateUsd = guideDailyRateUsd;
        this.passengerCapacity = passengerCapacity;
        this.destination = destination;
        this.available = available;
    }
}