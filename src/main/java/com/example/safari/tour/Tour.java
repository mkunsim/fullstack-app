package com.example.safari.tour;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tours")
@Data
@ToString(exclude = {"departures"})
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Tour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(nullable = false, length = 3000)
    private String description;

    @Column(nullable = false, length = 180)
    private String destination;

    @Column(nullable = false)
    private int durationDays;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerPerson;

    @Column(nullable = false, length = 1000)
    private String imageUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @OneToMany(mappedBy = "tour", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Departure> departures = new ArrayList<>();

    public Tour(String name, String description, String destination, int durationDays, BigDecimal pricePerPerson, String imageUrl) {
        this.name = name;
        this.description = description;
        this.destination = destination;
        this.durationDays = durationDays;
        this.pricePerPerson = pricePerPerson;
        this.imageUrl = imageUrl;
        this.active = true;
        this.departures = new ArrayList<>();
    }

    public void update(String name, String description, String destination, int durationDays, BigDecimal price, String imageUrl, boolean active) {
        this.name = name;
        this.description = description;
        this.destination = destination;
        this.durationDays = durationDays;
        this.pricePerPerson = price;
        this.imageUrl = imageUrl;
        this.active = active;
    }
}