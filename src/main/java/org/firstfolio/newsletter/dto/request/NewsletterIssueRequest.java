package org.firstfolio.newsletter.dto.request;

import java.util.List;

public record NewsletterIssueRequest(
        String title,
        String summary,
        String relatedTerm,
        List<NewsletterSourceRequest> sources
) {
}
