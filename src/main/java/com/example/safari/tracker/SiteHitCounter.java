package com.example.safari.tracker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "site_hit_counters")
public class SiteHitCounter {
    public static final short SINGLETON_ID = 1;

    @Id
    private short id;

    @Column(name = "total_count", nullable = false)
    private long totalCount;

    protected SiteHitCounter() {}

    public SiteHitCounter(short id, long totalCount) {
        this.id = id;
        this.totalCount = totalCount;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void increment() {
        totalCount++;
    }
}
