package org.firstfolio.newsletter.dto.response;

import java.util.List;

public record NewsletterListResponse(
        List<NewsletterDetailResponse> items
) {
}
