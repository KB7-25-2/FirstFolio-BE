package org.firstfolio.curriculum.domain;

import java.util.List;

public record CurriculumDraftResult(
        List<CurriculumDraftItem> items,
        List<CurriculumDraftCandidate> recommendationCandidates,
        List<CurriculumDraftCandidate> cartCandidates
) {
    public CurriculumDraftResult {
        items = List.copyOf(items);
        recommendationCandidates = List.copyOf(recommendationCandidates);
        cartCandidates = List.copyOf(cartCandidates);
    }
}
