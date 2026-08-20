package org.firstfolio.newsletter.dto.response;

import java.util.List;

public record NewsletterIssueResponse(
        String title,
        String summary,
        String relatedTerm,
        List<NewsletterSourceResponse> sources
) {
}
