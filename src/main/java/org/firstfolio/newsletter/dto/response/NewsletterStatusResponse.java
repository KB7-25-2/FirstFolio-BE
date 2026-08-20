package org.firstfolio.newsletter.dto.response;

import org.firstfolio.newsletter.domain.Newsletter;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record NewsletterStatusResponse(
        Long newsletterId,
        LocalDate weekStartDate,
        String status,
        LocalDateTime publishedAt
) {

    public static NewsletterStatusResponse from(Newsletter newsletter) {
        return new NewsletterStatusResponse(
                newsletter.getNewsletterId(),
                newsletter.getWeekStartDate(),
                newsletter.getStatus().name(),
                newsletter.getPublishedAt()
        );
    }
}
