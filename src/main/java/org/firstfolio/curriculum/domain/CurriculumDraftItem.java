package org.firstfolio.curriculum.domain;

public record CurriculumDraftItem(
        long mainChapterId,
        String title,
        CurriculumSourceType sourceType,
        int displayOrder,
        boolean removable
) {
}
