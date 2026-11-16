package com.example.safari.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "site_reviews")
public class SiteReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int rating;

    @Column(name = "review_text", nullable = false, length = 3000)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SiteReview() {}

    public SiteReview(int rating, String text) {
        this.rating = rating;
        this.text = text;
    }

    @PrePersist
    protected void setCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public int getRating() {
        return rating;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
