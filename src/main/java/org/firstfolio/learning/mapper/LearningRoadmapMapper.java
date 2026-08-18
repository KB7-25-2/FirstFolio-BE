package org.firstfolio.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.learning.domain.LearningProgressStatus;
import org.firstfolio.quiz.domain.QuizAttemptStatus;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningRoadmapMapper {

    List<ChapterRow> findChaptersByUserId(
            @Param("userId") long userId
    );

    List<SubChapterRow> findSubChaptersByUserId(
            @Param("userId") long userId
    );

    List<QuizRow> findMainChapterQuizzesByUserId(
            @Param("userId") long userId
    );

    record ChapterRow(
            long curriculumItemId,
            long mainChapterId,
            String title,
            String description,
            ChapterType chapterType,
            int displayOrder,
            CurriculumSourceType sourceType,
            CurriculumItemStatus curriculumStatus,
            LocalDateTime completedAt,
            int progressPercent
    ) {
    }

    record SubChapterRow(
            long mainChapterId,
            long subChapterId,
            String title,
            String description,
            int displayOrder,
            Long currentContentVersionId,
            Long progressId,
            Long progressContentVersionId,
            LearningProgressStatus progressStatus,
            String lastPageId,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime updatedAt
    ) {
        public boolean contentAvailable() {
            return currentContentVersionId != null;
        }
    }

    record QuizRow(
            long mainChapterId,
            boolean questionAvailable,
            boolean allSubChaptersCompleted,
            Long attemptId,
            QuizAttemptStatus attemptStatus,
            Integer totalCount,
            Integer correctCount,
            Integer score,
            LocalDateTime startedAt,
            LocalDateTime submittedAt
    ) {
    }
}
