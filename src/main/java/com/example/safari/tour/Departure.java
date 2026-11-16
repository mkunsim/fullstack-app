package com.example.safari.tour;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "departures",
        indexes = @Index(
                name = "idx_departures_tour_id_start_date",
                columnList = "tour_id,start_date"
        )
)
@Data
@ToString(exclude = "tour")
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Departure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tour_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_departures_tour_id")
    )
    private Tour tour;

    @Column(name = "start_date", nullable = false)
    private java.time.LocalDate startDate;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int reservedGuests;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    public Departure(Tour tour, java.time.LocalDate startDate, int capacity) {
        this.tour = tour;
        this.startDate = startDate;
        this.capacity = capacity;
        this.reservedGuests = 0;
        this.active = true;
    }

    public int getAvailableGuests() {
        return capacity - reservedGuests;
    }

    public void reserve(int guests) {
        if (!active || guests < 1 || guests > getAvailableGuests()) {
            throw new IllegalStateException("Departure has insufficient availability");
        }
        reservedGuests += guests;
    }

    public void release(int guests) {
        reservedGuests = Math.max(0, reservedGuests - guests);
    }
}