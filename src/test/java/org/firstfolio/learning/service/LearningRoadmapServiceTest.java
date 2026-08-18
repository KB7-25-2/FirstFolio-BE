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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningRoadmapServiceTest {

    private static final long USER_ID = 11L;
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 14, 3, 0);

    private LearningRoadmapMapper roadmapMapper;
    private LearningRoadmapService service;

    @BeforeEach
    void setUp() {
        roadmapMapper = mock(LearningRoadmapMapper.class);
        service = new LearningRoadmapService(roadmapMapper);
    }

    @Test
    void aggregatesCurriculumSubChapterProgressAndQuizState() {
        when(roadmapMapper.findChaptersByUserId(USER_ID)).thenReturn(List.of(
                chapter(501L, 8L, ChapterType.FOUNDATION,
                        CurriculumSourceType.FOUNDATION, 1, null, 50),
                chapter(502L, 2L, ChapterType.ASSET,
                        CurriculumSourceType.USER_ADDED, 2, null, 100)
        ));
        when(roadmapMapper.findSubChaptersByUserId(USER_ID)).thenReturn(List.of(
                subChapter(8L, 101L, 1, 301L, 701L, 301L,
                        LearningProgressStatus.COMPLETED),
                subChapter(8L, 102L, 2, 302L, null, null, null),
                subChapter(2L, 201L, 1, 401L, 702L, 401L,
                        LearningProgressStatus.COMPLETED)
        ));
        when(roadmapMapper.findMainChapterQuizzesByUserId(USER_ID))
                .thenReturn(List.of(
                        quiz(8L, true, false, null, null),
                        quiz(2L, true, true, 801L,
                                QuizAttemptStatus.GRADED)
                ));

        LearningRoadmapResponse result = service.getRoadmap(USER_ID);

        assertEquals(2, result.items().size());
        LearningRoadmapResponse.Chapter foundation = result.items().get(0);
        assertEquals(LearningRoadmapStatus.Chapter.IN_PROGRESS,
                foundation.status());
        assertEquals(2, foundation.subChapters().size());
        assertEquals(LearningRoadmapStatus.Schedule.COMPLETED,
                foundation.subChapters().get(0).scheduleStatus());
        assertEquals(LearningRoadmapStatus.Schedule.NEXT,
                foundation.subChapters().get(1).scheduleStatus());
        assertEquals(LearningProgressStatus.NOT_STARTED,
                foundation.subChapters().get(1).progressStatus());
        assertNull(foundation.subChapters().get(1).progressId());
        assertEquals(LearningRoadmapStatus.Quiz.LOCKED,
                foundation.mainChapterQuiz().status());
        assertFalse(foundation.mainChapterQuiz().available());

        LearningRoadmapResponse.Chapter asset = result.items().get(1);
        assertEquals(LearningRoadmapStatus.Chapter.LOCKED,
                asset.status());
        assertEquals(LearningRoadmapStatus.Schedule.LOCKED,
                asset.subChapters().get(0).scheduleStatus());
        assertEquals(LearningRoadmapStatus.Quiz.LOCKED,
                asset.mainChapterQuiz().status());
        assertEquals(801L, asset.mainChapterQuiz().attemptId());

        verify(roadmapMapper).findChaptersByUserId(USER_ID);
        verify(roadmapMapper).findSubChaptersByUserId(USER_ID);
        verify(roadmapMapper).findMainChapterQuizzesByUserId(USER_ID);
    }

    @Test
    void marksContentWithoutPublishedVersionAsUnavailable() {
        when(roadmapMapper.findChaptersByUserId(USER_ID)).thenReturn(List.of(
                chapter(501L, 8L, ChapterType.FOUNDATION,
                        CurriculumSourceType.FOUNDATION, 1, null, 0)
        ));
        when(roadmapMapper.findSubChaptersByUserId(USER_ID)).thenReturn(List.of(
                subChapter(8L, 101L, 1, null, null, null, null)
        ));
        when(roadmapMapper.findMainChapterQuizzesByUserId(USER_ID))
                .thenReturn(List.of(quiz(8L, true, false, null, null)));

        LearningRoadmapResponse.Chapter chapter =
                service.getRoadmap(USER_ID).items().get(0);

        assertEquals(LearningRoadmapStatus.Schedule.UNAVAILABLE,
                chapter.subChapters().get(0).scheduleStatus());
        assertFalse(chapter.subChapters().get(0).contentAvailable());
        assertEquals(LearningRoadmapStatus.Quiz.LOCKED,
                chapter.mainChapterQuiz().status());
        assertFalse(chapter.mainChapterQuiz().available());
    }

    @Test
    void marksMainChapterQuizAsAvailableAfterSubChaptersComplete() {
        when(roadmapMapper.findChaptersByUserId(USER_ID)).thenReturn(List.of(
                chapter(501L, 8L, ChapterType.FOUNDATION,
                        CurriculumSourceType.FOUNDATION, 1, null, 100)
        ));
        when(roadmapMapper.findSubChaptersByUserId(USER_ID)).thenReturn(List.of(
                subChapter(8L, 101L, 1, 301L, 701L, 301L,
                        LearningProgressStatus.COMPLETED)
        ));
        when(roadmapMapper.findMainChapterQuizzesByUserId(USER_ID))
                .thenReturn(List.of(quiz(8L, true, true, null, null)));

        LearningRoadmapResponse.Chapter chapter =
                service.getRoadmap(USER_ID).items().get(0);

        assertEquals(LearningRoadmapStatus.Quiz.AVAILABLE,
                chapter.mainChapterQuiz().status());
        assertTrue(chapter.mainChapterQuiz().available());
    }

    @Test
    void marksCompletedChapterAndQuizAsCompleted() {
        when(roadmapMapper.findChaptersByUserId(USER_ID)).thenReturn(List.of(
                chapter(501L, 8L, ChapterType.FOUNDATION,
                        CurriculumSourceType.FOUNDATION, 1, NOW, 100)
        ));
        when(roadmapMapper.findSubChaptersByUserId(USER_ID)).thenReturn(List.of(
                subChapter(8L, 101L, 1, 301L, 701L, 301L,
                        LearningProgressStatus.COMPLETED)
        ));
        when(roadmapMapper.findMainChapterQuizzesByUserId(USER_ID))
                .thenReturn(List.of(
                        quiz(8L, true, true, 801L,
                                QuizAttemptStatus.GRADED)
                ));

        LearningRoadmapResponse.Chapter chapter =
                service.getRoadmap(USER_ID).items().get(0);

        assertEquals(LearningRoadmapStatus.Chapter.COMPLETED,
                chapter.status());
        assertEquals(LearningRoadmapStatus.Quiz.COMPLETED,
                chapter.mainChapterQuiz().status());
        assertFalse(chapter.mainChapterQuiz().available());
    }

    @Test
    void returnsNotFoundWhenConfirmedCurriculumDoesNotExist() {
        when(roadmapMapper.findChaptersByUserId(USER_ID)).thenReturn(List.of());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getRoadmap(USER_ID)
        );

        assertEquals(ErrorCode.CURRICULUM_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void rejectsRoadmapRowsThatDoNotMatchCurriculum() {
        when(roadmapMapper.findChaptersByUserId(USER_ID)).thenReturn(List.of(
                chapter(501L, 8L, ChapterType.FOUNDATION,
                        CurriculumSourceType.FOUNDATION, 1, null, 0)
        ));
        when(roadmapMapper.findSubChaptersByUserId(USER_ID)).thenReturn(List.of(
                subChapter(99L, 101L, 1, 301L, null, null, null)
        ));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getRoadmap(USER_ID)
        );

        assertEquals(ErrorCode.CURRICULUM_CONFIGURATION_INVALID,
                exception.getErrorCode());
    }

    private ChapterRow chapter(
            long curriculumItemId,
            long mainChapterId,
            ChapterType chapterType,
            CurriculumSourceType sourceType,
            int displayOrder,
            LocalDateTime completedAt,
            int progressPercent
    ) {
        return new ChapterRow(
                curriculumItemId,
                mainChapterId,
                chapterType == ChapterType.FOUNDATION
                        ? "포트폴리오 기초" : "예·적금",
                "대단원 설명",
                chapterType,
                displayOrder,
                sourceType,
                CurriculumItemStatus.ACTIVE,
                completedAt,
                progressPercent
        );
    }

    private SubChapterRow subChapter(
            long mainChapterId,
            long subChapterId,
            int displayOrder,
            Long currentContentVersionId,
            Long progressId,
            Long progressContentVersionId,
            LearningProgressStatus progressStatus
    ) {
        return new SubChapterRow(
                mainChapterId,
                subChapterId,
                "소단원 " + displayOrder,
                "소단원 설명",
                displayOrder,
                currentContentVersionId,
                progressId,
                progressContentVersionId,
                progressStatus,
                progressId == null ? null : "page-1",
                progressId == null ? null : NOW.minusDays(1),
                progressStatus == LearningProgressStatus.COMPLETED ? NOW : null,
                progressId == null ? null : NOW
        );
    }

    private QuizRow quiz(
            long mainChapterId,
            boolean questionAvailable,
            boolean allSubChaptersCompleted,
            Long attemptId,
            QuizAttemptStatus attemptStatus
    ) {
        return new QuizRow(
                mainChapterId,
                questionAvailable,
                allSubChaptersCompleted,
                attemptId,
                attemptStatus,
                attemptId == null ? null : 3,
                attemptId == null ? null : 2,
                attemptId == null ? null : 67,
                attemptId == null ? null : NOW.minusHours(1),
                attemptStatus == QuizAttemptStatus.GRADED ? NOW : null
        );
    }
}
