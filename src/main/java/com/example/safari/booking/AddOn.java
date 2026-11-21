
package com.example.safari.booking;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "addons")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AddOn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "preference", nullable = false, length = 1000)
    private String preference;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Builder
    public AddOn(
            String name,
            String description,
            String preference,
            BigDecimal price,
            boolean available
    ) {
        update(
                name,
                description,
                preference,
                price,
                available
        );
    }

    public AddOn(
            Long id,
            String name,
            String description,
            String preference,
            BigDecimal price,
            boolean available
    ) {
        this.id = id;
        update(
                name,
                description,
                preference,
                price,
                available
        );
    }

    public void update(
            String name,
            String description,
            String preference,
            BigDecimal price,
            boolean available
    ) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Add-on name is required."
            );
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Add-on description is required."
            );
        }

        if (preference == null) {
            throw new IllegalArgumentException(
                    "Add-on service requirements are required."
            );
        }

        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException(
                    "Add-on price cannot be negative."
            );
        }

        this.name = name.trim();
        this.description = description.trim();
        this.preference = preference.trim();
        this.price = price;
        this.available = available;
    }

    @PrePersist
    @PreUpdate
    private void validatePrice() {

        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException(
                    "Add-on price cannot be negative."
            );
        }
    }
}
