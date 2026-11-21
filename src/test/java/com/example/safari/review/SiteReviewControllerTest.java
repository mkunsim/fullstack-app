package com.example.safari.review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteReviewControllerTest {
    @Mock
    private SiteReviewRepository reviews;

    @Test
    @SuppressWarnings("null") // Mockito matchers use null as a stubbing sentinel.
    void storesRatingAndTrimmedReviewText() {
        SiteReviewController controller = new SiteReviewController(reviews);
        when(reviews.save(any(SiteReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, SiteReview.class));

        var response = controller.submit(new SiteReviewController.ReviewRequest(5, "  Loved our safari!  "));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("Review submitted. Thank you.", response.getBody().message());
        ArgumentCaptor<SiteReview> savedReview = ArgumentCaptor.forClass(SiteReview.class);
        verify(reviews).save(savedReview.capture());
        assertEquals(5, savedReview.getValue().getRating());
        assertEquals("Loved our safari!", savedReview.getValue().getText());
    }
}
