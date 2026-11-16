package com.example.safari.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class SiteReviewController {
    private final SiteReviewRepository reviews;

    public SiteReviewController(SiteReviewRepository reviews) {
        this.reviews = reviews;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> submit(@Valid @RequestBody ReviewRequest request) {
        reviews.save(new SiteReview(request.rating(), request.text().trim()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ReviewResponse("Review submitted. Thank you."));
    }

    public record ReviewRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @NotBlank @Size(max = 3000) String text
    ) {}

    public record ReviewResponse(String message) {}
}
