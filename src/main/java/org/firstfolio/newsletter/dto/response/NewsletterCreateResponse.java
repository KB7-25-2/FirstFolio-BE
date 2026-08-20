package org.firstfolio.newsletter.dto.response;

import org.firstfolio.newsletter.domain.Newsletter;

import java.time.LocalDate;

public record NewsletterCreateResponse(
        Long newsletterId,
        LocalDate weekStartDate,
        String status
) {
    public static NewsletterCreateResponse from(Newsletter newsletter) {
        return new NewsletterCreateResponse(
                newsletter.getNewsletterId(),
                newsletter.getWeekStartDate(),
                newsletter.getStatus().name()
        );
    }
}
