package com.example.safari.tracker;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SiteHitCounterInitializer implements ApplicationRunner {
    private final SiteHitCounterRepository counters;

    public SiteHitCounterInitializer(SiteHitCounterRepository counters) {
        this.counters = counters;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!counters.existsById(SiteHitCounter.SINGLETON_ID)) {
            counters.save(new SiteHitCounter(SiteHitCounter.SINGLETON_ID, 0));
        }
    }
}
