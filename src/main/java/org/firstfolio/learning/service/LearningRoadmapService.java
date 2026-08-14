package org.firstfolio.learning.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.CurriculumItemStatus;
import org.firstfolio.curriculum.domain.CurriculumSourceType;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningProgressStatus;
import org.firstfolio.learning.domain.LearningRoadmapStatus;
import org.firstfolio.learning.dto.response.LearningRoadmapResponse;
import org.firstfolio.learning.mapper.LearningRoadmapMapper;
import org.firstfolio.learning.mapper.LearningRoadmapMapper.ChapterRow;
import org.firstfolio.learning.mapper.LearningRoadmapMapper.QuizRow;
import org.firstfolio.learning.mapper.LearningRoadmapMapper.SubChapterRow;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LearningRoadmapService {

    private final LearningRoadmapMapper roadmapMapper;

    public LearningRoadmapService(LearningRoadmapMapper roadmapMapper) {
        this.roadmapMapper = roadmapMapper;
    }

    @Transactional(readOnly = true)
    public LearningRoadmapResponse getRoadmap(long userId) {
        List<ChapterRow> chapterRows =
                roadmapMapper.findChaptersByUserId(userId);
        if (chapterRows.isEmpty()) {
            throw new ApiException(ErrorCode.CURRICULUM_NOT_FOUND);
        }
        validateChapters(chapterRows);

        Map<Long, List<SubChapterRow>> subChaptersByMain =
                groupSubChapters(
                        chapterRows,
                        roadmapMapper.findSubChaptersByUserId(userId)
                );
        Map<Long, QuizRow> quizzesByMain = groupQuizzes(
                chapterRows,
                roadmapMapper.findMainChapterQuizzesByUserId(userId)
        );

        boolean inProgressAssigned = false;
        List<LearningRoadmapResponse.Chapter> chapters = new ArrayList<>();
        for (ChapterRow chapterRow : chapterRows) {
            LearningRoadmapStatus.Chapter chapterStatus;
            if (chapterRow.completedAt() != null) {
                chapterStatus = LearningRoadmapStatus.Chapter.COMPLETED;
            } else if (!inProgressAssigned) {
                chapterStatus = LearningRoadmapStatus.Chapter.IN_PROGRESS;
                inProgressAssigned = true;
            } else {
                chapterStatus = LearningRoadmapStatus.Chapter.LOCKED;
            }

            List<LearningRoadmapResponse.SubChapter> subChapters =
                    buildSubChapters(
                            subChaptersByMain.getOrDefault(
                                    chapterRow.mainChapterId(),
                                    List.of()
                            ),
                            chapterStatus
                    );
            LearningRoadmapResponse.MainChapterQuiz quiz = buildQuiz(
                    quizzesByMain.get(chapterRow.mainChapterId()),
                    chapterRow.completedAt() != null,
                    chapterStatus
            );

            chapters.add(new LearningRoadmapResponse.Chapter(
                    chapterRow.curriculumItemId(),
                    chapterRow.mainChapterId(),
                    chapterRow.title(),
                    chapterRow.description(),
                    chapterRow.chapterType(),
                    chapterRow.displayOrder(),
                    chapterStatus,
                    chapterRow.completedAt(),
                    chapterRow.progressPercent(),
                    List.copyOf(subChapters),
                    quiz
            ));
        }
        return new LearningRoadmapResponse(List.copyOf(chapters));
    }

    private void validateChapters(List<ChapterRow> rows) {
        Set<Long> curriculumItemIds = new HashSet<>();
        Set<Long> mainChapterIds = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            ChapterRow row = rows.get(index);
            boolean foundation = index == 0;
            if (row.curriculumItemId() <= 0
                    || row.mainChapterId() <= 0
                    || row.title() == null
                    || row.title().isBlank()
                    || row.displayOrder() != index + 1
                    || row.curriculumStatus() != CurriculumItemStatus.ACTIVE
                    || row.sourceType() == null
                    || row.chapterType() == null
                    || row.progressPercent() < 0
                    || row.progressPercent() > 100
                    || (row.completedAt() != null
                    && row.progressPercent() != 100)
                    || !curriculumItemIds.add(row.curriculumItemId())
                    || !mainChapterIds.add(row.mainChapterId())
                    || !hasValidChapterRole(row, foundation)) {
                throw invalidConfiguration();
            }
        }
    }

    private boolean hasValidChapterRole(
            ChapterRow row,
            boolean foundation
    ) {
        if (foundation) {
            return row.chapterType() == ChapterType.FOUNDATION
                    && row.sourceType() == CurriculumSourceType.FOUNDATION;
        }
        return row.chapterType() == ChapterType.ASSET
                && row.sourceType() != CurriculumSourceType.FOUNDATION;
    }

    private Map<Long, List<SubChapterRow>> groupSubChapters(
            List<ChapterRow> chapters,
            List<SubChapterRow> subChapters
    ) {
        Set<Long> allowedMainChapterIds = new HashSet<>();
        for (ChapterRow chapter : chapters) {
            allowedMainChapterIds.add(chapter.mainChapterId());
        }

        Map<Long, List<SubChapterRow>> grouped =
                new LinkedHashMap<>();
        Map<Long, Integer> lastOrders = new HashMap<>();
        Set<Long> subChapterIds = new HashSet<>();
        for (SubChapterRow row : subChapters) {
            int previousOrder = lastOrders.getOrDefault(
                    row.mainChapterId(),
                    0
            );
            if (!allowedMainChapterIds.contains(row.mainChapterId())
                    || row.subChapterId() <= 0
                    || row.title() == null
                    || row.title().isBlank()
                    || row.displayOrder() <= previousOrder
                    || !subChapterIds.add(row.subChapterId())
                    || (row.progressId() == null
                    && row.progressStatus() != null)
                    || (row.progressId() != null
                    && (row.progressStatus() == null
                    || row.progressContentVersionId() == null))) {
                throw invalidConfiguration();
            }
            lastOrders.put(row.mainChapterId(), row.displayOrder());
            grouped.computeIfAbsent(
                    row.mainChapterId(),
                    ignored -> new ArrayList<>()
            ).add(row);
        }
        return grouped;
    }

    private Map<Long, QuizRow> groupQuizzes(
            List<ChapterRow> chapters,
            List<QuizRow> quizzes
    ) {
        Set<Long> allowedMainChapterIds = new HashSet<>();
        for (ChapterRow chapter : chapters) {
            allowedMainChapterIds.add(chapter.mainChapterId());
        }

        Map<Long, QuizRow> grouped = new HashMap<>();
        for (QuizRow row : quizzes) {
            if (!allowedMainChapterIds.contains(row.mainChapterId())
                    || grouped.put(row.mainChapterId(), row) != null
                    || (row.attemptId() == null
                    && row.attemptStatus() != null)
                    || (row.attemptId() != null
                    && (row.attemptStatus() == null
                    || row.totalCount() == null
                    || row.correctCount() == null
                    || row.score() == null))) {
                throw invalidConfiguration();
            }
        }
        if (!grouped.keySet().equals(allowedMainChapterIds)) {
            throw invalidConfiguration();
        }
        return grouped;
    }

    private List<LearningRoadmapResponse.SubChapter> buildSubChapters(
            List<SubChapterRow> rows,
            LearningRoadmapStatus.Chapter chapterStatus
    ) {
        boolean nextAssigned = false;
        List<LearningRoadmapResponse.SubChapter> results = new ArrayList<>();
        for (SubChapterRow row : rows) {
            LearningProgressStatus progressStatus = row.progressStatus() == null
                    ? LearningProgressStatus.NOT_STARTED
                    : row.progressStatus();
            LearningRoadmapStatus.Schedule scheduleStatus;
            if (chapterStatus == LearningRoadmapStatus.Chapter.LOCKED) {
                scheduleStatus = LearningRoadmapStatus.Schedule.LOCKED;
            } else if (progressStatus == LearningProgressStatus.COMPLETED) {
                scheduleStatus = LearningRoadmapStatus.Schedule.COMPLETED;
            } else if (!row.contentAvailable()) {
                scheduleStatus = LearningRoadmapStatus.Schedule.UNAVAILABLE;
                nextAssigned = true;
            } else if (progressStatus == LearningProgressStatus.IN_PROGRESS) {
                scheduleStatus = LearningRoadmapStatus.Schedule.IN_PROGRESS;
                nextAssigned = true;
            } else if (!nextAssigned) {
                scheduleStatus = LearningRoadmapStatus.Schedule.NEXT;
                nextAssigned = true;
            } else {
                scheduleStatus = LearningRoadmapStatus.Schedule.LOCKED;
            }

            results.add(new LearningRoadmapResponse.SubChapter(
                    row.subChapterId(),
                    row.title(),
                    row.description(),
                    row.displayOrder(),
                    row.currentContentVersionId(),
                    row.contentAvailable(),
                    row.progressId(),
                    row.progressContentVersionId(),
                    progressStatus,
                    row.lastPageId(),
                    row.startedAt(),
                    row.completedAt(),
                    row.updatedAt(),
                    scheduleStatus
            ));
        }
        return results;
    }

    private LearningRoadmapResponse.MainChapterQuiz buildQuiz(
            QuizRow row,
            boolean chapterCompleted,
            LearningRoadmapStatus.Chapter chapterStatus
    ) {
        LearningRoadmapStatus.Quiz status;
        if (chapterCompleted) {
            status = LearningRoadmapStatus.Quiz.COMPLETED;
        } else if (chapterStatus == LearningRoadmapStatus.Chapter.LOCKED) {
            status = LearningRoadmapStatus.Quiz.LOCKED;
        } else if (row.attemptStatus() == QuizAttemptStatus.IN_PROGRESS) {
            status = LearningRoadmapStatus.Quiz.IN_PROGRESS;
        } else if (row.questionAvailable()
                && row.allSubChaptersCompleted()) {
            status = LearningRoadmapStatus.Quiz.AVAILABLE;
        } else {
            status = LearningRoadmapStatus.Quiz.LOCKED;
        }
        boolean available = status == LearningRoadmapStatus.Quiz.AVAILABLE
                || status == LearningRoadmapStatus.Quiz.IN_PROGRESS;

        return new LearningRoadmapResponse.MainChapterQuiz(
                row.questionAvailable(),
                available,
                status,
                row.attemptId(),
                row.attemptStatus(),
                row.totalCount(),
                row.correctCount(),
                row.score(),
                row.startedAt(),
                row.submittedAt()
        );
    }

    private ApiException invalidConfiguration() {
        return new ApiException(ErrorCode.CURRICULUM_CONFIGURATION_INVALID);
    }
}
