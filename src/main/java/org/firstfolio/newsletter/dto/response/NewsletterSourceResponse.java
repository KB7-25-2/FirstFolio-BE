package org.firstfolio.newsletter.dto.response;

public record NewsletterSourceResponse(
        Long documentId,
        String chunkKey,
        String sourceUrl,
        String evidenceText
) {
}
