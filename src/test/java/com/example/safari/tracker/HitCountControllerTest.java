package com.example.safari.tracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HitCountControllerTest {
    @Mock
    private SiteHitCounterRepository counters;

    @Mock
    private SitePageHitRepository pageHits;

    @SuppressWarnings("null")
    @Test
    void incrementsCounterAndRecordsFirstVisitForPage() {
        SiteHitCounter counter = new SiteHitCounter(SiteHitCounter.SINGLETON_ID, 41);
        when(counters.findByIdForUpdate(SiteHitCounter.SINGLETON_ID)).thenReturn(Optional.of(counter));
        when(pageHits.findById("/safari-tours")).thenReturn(Optional.empty());

        HitCountController.HitCountResponse response =
                new HitCountController(counters, pageHits)
                        .recordHit(new HitCountController.HitRequest("/safari-tours"));

        assertEquals(42, response.totalCount());
        verify(pageHits).save(org.mockito.ArgumentMatchers.argThat(
                pageHit -> pageHit.getVisitCount() == 1
        ));
    }

    @Test
    void incrementsExistingPageAggregate() {
        SiteHitCounter counter = new SiteHitCounter(SiteHitCounter.SINGLETON_ID, 0);
        SitePageHit pageHit = new SitePageHit("/");
        when(counters.findByIdForUpdate(SiteHitCounter.SINGLETON_ID)).thenReturn(Optional.of(counter));
        when(pageHits.findById("/")).thenReturn(Optional.of(pageHit));

        new HitCountController(counters, pageHits)
                .recordHit(new HitCountController.HitRequest("/"));

        assertEquals(2, pageHit.getVisitCount());
    }

    @Test
    void failsClearlyWhenCounterRowIsMissing() {
        when(counters.findByIdForUpdate(SiteHitCounter.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> new HitCountController(counters, pageHits)
                        .recordHit(new HitCountController.HitRequest("/"))
        );
    }
}
