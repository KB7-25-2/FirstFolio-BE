package org.firstfolio.newsletter.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NewsletterTest {

    @Test
    void reviewFactoryStartsInReviewStatusWithoutPublishedAt() {
        Newsletter newsletter = Newsletter.review(
                LocalDate.of(2026, 8, 17),
                "역대 최대 흑자 속에서도, 돈은 안전자산으로",
                "[]",
                "[]",
                "[]",
                NewsletterGenerationType.AI,
                1L,
                LocalDateTime.of(2026, 8, 20, 9, 0)
        );

        assertEquals(NewsletterStatus.REVIEW, newsletter.getStatus());
        assertNull(newsletter.getPublishedAt());
    }

    @Test
    void publishSetsStatusAndPublishedAt() {
        Newsletter newsletter = reviewSample();
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 20, 10, 0);

        newsletter.publish(publishedAt);

        assertEquals(NewsletterStatus.PUBLISHED, newsletter.getStatus());
        assertEquals(publishedAt, newsletter.getPublishedAt());
    }

    @Test
    void retireSetsStatusToRetired() {
        Newsletter newsletter = reviewSample();
        newsletter.publish(LocalDateTime.of(2026, 8, 20, 10, 0));

        newsletter.retire();

        assertEquals(NewsletterStatus.RETIRED, newsletter.getStatus());
    }

    private Newsletter reviewSample() {
        return Newsletter.review(
                LocalDate.of(2026, 8, 17),
                "역대 최대 흑자 속에서도, 돈은 안전자산으로",
                "[]",
                "[]",
                "[]",
                NewsletterGenerationType.AI,
                1L,
                LocalDateTime.of(2026, 8, 20, 9, 0)
        );
    }
}
