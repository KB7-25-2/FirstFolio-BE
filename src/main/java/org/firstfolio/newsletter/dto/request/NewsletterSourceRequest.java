package org.firstfolio.newsletter.dto.request;

public record NewsletterSourceRequest(
        Long documentId,
        String chunkKey,
        String sourceUrl,
        String evidenceText
) {
}
