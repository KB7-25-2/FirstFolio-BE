package org.firstfolio.curriculum.domain;

import java.time.LocalDateTime;

public record CurriculumOverviewItem(
        long curriculumItemId,
        long mainChapterId,
        String title,
        ChapterType chapterType,
        int displayOrder,
        CurriculumSourceType sourceType,
        CurriculumItemStatus status,
        LocalDateTime completedAt,
        int progressPercent
) {
}
