package com.example.safari.tracker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "site_page_hits")
public class SitePageHit {
    @Id
    @Column(name = "page_path", length = 2048)
    private String pagePath;

    @Column(name = "visit_count", nullable = false)
    private long visitCount;

    @Column(name = "last_visited_at", nullable = false)
    private Instant lastVisitedAt;

    protected SitePageHit() {}

    public SitePageHit(String pagePath) {
        this.pagePath = pagePath;
        this.visitCount = 1;
        this.lastVisitedAt = Instant.now();
    }

    public void recordVisit() {
        visitCount++;
        lastVisitedAt = Instant.now();
    }

    public long getVisitCount() {
        return visitCount;
    }
}
