package com.example.safari.tracker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/hits")
public class HitCountController {
    private final SiteHitCounterRepository counters;
    private final SitePageHitRepository pageHits;

    public HitCountController(SiteHitCounterRepository counters, SitePageHitRepository pageHits) {
        this.counters = counters;
        this.pageHits = pageHits;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.OK)
    public HitCountResponse recordHit(@Valid @RequestBody HitRequest hit) {
        if (hit == null || hit.pagePath() == null || hit.pagePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pagePath is required.");
        }
        SiteHitCounter counter = counters.findByIdForUpdate(SiteHitCounter.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("The site hit counter row is missing."));
        counter.increment();

        if (hit == null || hit.pagePath() == null || hit.pagePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pagePath is required.");
        }

        String pagePath = Objects.requireNonNull(hit.pagePath());

        pageHits.findById(pagePath)
        .ifPresentOrElse(
                pageHit -> pageHit.recordVisit(),
                () -> pageHits.save(new SitePageHit(pagePath))
        );

        return new HitCountResponse(counter.getTotalCount());
    }

    public record HitRequest(
            @NotBlank @Size(max = 2048) @Pattern(regexp = "^/[^?#]*$") String pagePath
    ) {}

    public record HitCountResponse(long totalCount) {}
}
