package com.example.safari.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SiteReviewRepository extends JpaRepository<SiteReview, Long> {

    @Query("select avg(review.rating) from SiteReview review")
    Double averageRating();
}
