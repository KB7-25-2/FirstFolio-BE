package org.firstfolio.learning.service;

import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.PublishedLessonReference;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningProgress;
import org.firstfolio.learning.domain.LearningProgressEvent;
import org.firstfolio.learning.domain.LearningProgressStatus;
import org.firstfolio.learning.domain.LearningProgressStatusResult;
import org.firstfolio.learning.domain.LearningProgressUpdateCommand;
import org.firstfolio.learning.domain.LearningProgressUpdateResult;
import org.firstfolio.learning.domain.SubChapterQuizProgress;
import org.firstfolio.learning.mapper.LearningProgressMapper;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningProgressServiceTest {

    private static final long USER_ID = 11L;
    private static final long SUB_CHAPTER_ID = 101L;
    private static final long CONTENT_VERSION_ID = 301L;
    private static final Instant NOW = Instant.parse("2026-08-10T01:30:00Z");
    private static final StoredObjectRef STORED_OBJECT = new StoredObjectRef(
            "learning/sub-chapters/101/lesson.json",
            "storage-version-1"
    );

    private LearningProgressMapper learningProgressMapper;
    private ContentVersionMapper contentVersionMapper;
    private SubChapterMapper subChapterMapper;
    private QuizAttemptMapper quizAttemptMapper;
    private StaticContentStorage contentStorage;
    private LearningProgressService service;

    @BeforeEach
    void setUp() {
        learningProgressMapper = mock(LearningProgressMapper.class);
        contentVersionMapper = mock(ContentVersionMapper.class);
        subChapterMapper = mock(SubChapterMapper.class);
        quizAttemptMapper = mock(QuizAttemptMapper.class);
        contentStorage = mock(StaticContentStorage.class);
        service = new LearningProgressService(
                learningProgressMapper,
                contentVersionMapper,
                subChapterMapper,
                quizAttemptMapper,
                contentStorage,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(subChapterMapper.findById(SUB_CHAPTER_ID))
                .thenReturn(activeSubChapter());
        when(quizAttemptMapper.findSubChapterQuizProgress(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(quizProgress(false, null, 0, 0));
    }

    @Test
    void firstPutCreatesProgressForCurrentPublishedVersion() {
        LearningProgress stored = progress(LearningProgressStatus.IN_PROGRESS, "page-1");
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(null, stored);
        when(contentVersionMapper.findCurrentPublishedLesson(SUB_CHAPTER_ID))
                .thenReturn(publishedLesson());
        stubValidPage("page-1");
        when(learningProgressMapper.insertIfAbsent(any())).thenReturn(1);
        when(learningProgressMapper.insertEvent(any())).thenReturn(1);

        LearningProgressUpdateResult result = service.save(
                USER_ID,
                SUB_CHAPTER_ID,
                command("page-1", LearningProgressStatus.IN_PROGRESS)
        );

        assertTrue(result.updated());
        assertEquals(stored, result.progress());

        ArgumentCaptor<LearningProgress> progressCaptor =
                ArgumentCaptor.forClass(LearningProgress.class);
        verify(learningProgressMapper).insertIfAbsent(progressCaptor.capture());
        LearningProgress inserted = progressCaptor.getValue();
        assertEquals(CONTENT_VERSION_ID, inserted.getContentVersionId());
        assertEquals("page-1", inserted.getLastPageId());
        assertEquals(LearningProgressStatus.IN_PROGRESS, inserted.getStatus());
        assertEquals(LocalDateTime.of(2026, 8, 10, 1, 30), inserted.getStartedAt());

        ArgumentCaptor<LearningProgressEvent> eventCaptor =
                ArgumentCaptor.forClass(LearningProgressEvent.class);
        verify(learningProgressMapper).insertEvent(eventCaptor.capture());
        assertEquals(LearningProgressStatus.NOT_STARTED,
                eventCaptor.getValue().getPreviousStatus());
        assertEquals("PROGRESS_UPDATED", eventCaptor.getValue().getEventType());
    }

    @Test
    void existingPutUpdatesPageAndWritesHistory() {
        LearningProgress existing = progress(
                LearningProgressStatus.IN_PROGRESS,
                "page-1"
        );
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(existing);
        stubValidPage("page-2");
        when(learningProgressMapper.updateProgress(existing)).thenReturn(1);
        when(learningProgressMapper.insertEvent(any())).thenReturn(1);

        LearningProgressUpdateResult result = service.save(
                USER_ID,
                SUB_CHAPTER_ID,
                command("page-2", LearningProgressStatus.IN_PROGRESS)
        );

        assertTrue(result.updated());
        assertEquals("page-2", result.progress().getLastPageId());
        assertEquals(LocalDateTime.of(2026, 8, 10, 1, 30),
                result.progress().getUpdatedAt());
        verify(learningProgressMapper).updateProgress(existing);

        ArgumentCaptor<LearningProgressEvent> eventCaptor =
                ArgumentCaptor.forClass(LearningProgressEvent.class);
        verify(learningProgressMapper).insertEvent(eventCaptor.capture());
        assertEquals("page-1", eventCaptor.getValue().getPreviousPageId());
        assertEquals("page-2", eventCaptor.getValue().getLastPageId());
    }

    @Test
    void completionIsRecordedOnceAndLaterRequestsDoNotChangeIt() {
        LearningProgress existing = progress(
                LearningProgressStatus.IN_PROGRESS,
                "page-2"
        );
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(existing);
        stubValidPage("page-3");
        when(learningProgressMapper.updateProgress(existing)).thenReturn(1);
        when(learningProgressMapper.insertEvent(any())).thenReturn(1);

        LearningProgressUpdateResult completed = service.save(
                USER_ID,
                SUB_CHAPTER_ID,
                command("page-3", LearningProgressStatus.COMPLETED)
        );

        assertTrue(completed.updated());
        assertEquals(LearningProgressStatus.COMPLETED,
                completed.progress().getStatus());
        assertEquals(LocalDateTime.of(2026, 8, 10, 1, 30),
                completed.progress().getCompletedAt());

        ArgumentCaptor<LearningProgressEvent> eventCaptor =
                ArgumentCaptor.forClass(LearningProgressEvent.class);
        verify(learningProgressMapper).insertEvent(eventCaptor.capture());
        assertEquals("COMPLETED", eventCaptor.getValue().getEventType());

        LearningProgress alreadyCompleted = progress(
                LearningProgressStatus.COMPLETED,
                "page-3"
        );
        alreadyCompleted.setCompletedAt(LocalDateTime.of(2026, 8, 9, 4, 0));
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(alreadyCompleted);

        LearningProgressUpdateResult replay = service.save(
                USER_ID,
                SUB_CHAPTER_ID,
                command("page-3", LearningProgressStatus.COMPLETED)
        );

        assertFalse(replay.updated());
        assertEquals(LocalDateTime.of(2026, 8, 9, 4, 0),
                replay.progress().getCompletedAt());
    }

    @Test
    void identicalInProgressPutIsIdempotent() {
        LearningProgress existing = progress(
                LearningProgressStatus.IN_PROGRESS,
                "page-2"
        );
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(existing);
        stubValidPage("page-2");

        LearningProgressUpdateResult result = service.save(
                USER_ID,
                SUB_CHAPTER_ID,
                command("page-2", LearningProgressStatus.IN_PROGRESS)
        );

        assertFalse(result.updated());
        verify(learningProgressMapper, never()).updateProgress(any());
        verify(learningProgressMapper, never()).insertEvent(any());
    }

    @Test
    void lateRequestCannotMoveProgressBackToAnEarlierPage() {
        LearningProgress existing = progress(
                LearningProgressStatus.IN_PROGRESS,
                "page-3"
        );
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(existing);
        stubValidPage("page-1");

        LearningProgressUpdateResult result = service.save(
                USER_ID,
                SUB_CHAPTER_ID,
                command("page-1", LearningProgressStatus.IN_PROGRESS)
        );

        assertFalse(result.updated());
        assertEquals("page-3", result.progress().getLastPageId());
        verify(learningProgressMapper, never()).updateProgress(any());
        verify(learningProgressMapper, never()).insertEvent(any());
    }

    @Test
    void omittedPageKeepsCurrentPositionWhileCompleting() {
        LearningProgress existing = progress(
                LearningProgressStatus.IN_PROGRESS,
                "page-3"
        );
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(existing);
        stubValidPage("page-3");
        when(learningProgressMapper.updateProgress(existing)).thenReturn(1);
        when(learningProgressMapper.insertEvent(any())).thenReturn(1);

        LearningProgressUpdateResult result = service.save(
                USER_ID,
                SUB_CHAPTER_ID,
                command(null, LearningProgressStatus.COMPLETED)
        );

        assertTrue(result.updated());
        assertEquals("page-3", result.progress().getLastPageId());
        assertEquals(LearningProgressStatus.COMPLETED,
                result.progress().getStatus());
    }

    @Test
    void rejectsWrongContentVersionOrPageId() {
        LearningProgress existing = progress(
                LearningProgressStatus.IN_PROGRESS,
                "page-1"
        );
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(existing);

        ApiException mismatch = assertThrows(
                ApiException.class,
                () -> service.save(USER_ID, SUB_CHAPTER_ID,
                        new LearningProgressUpdateCommand(
                                999L,
                                "page-1",
                                LearningProgressStatus.IN_PROGRESS
                        ))
        );
        assertEquals(ErrorCode.CONTENT_VERSION_MISMATCH, mismatch.getErrorCode());

        stubValidPage("page-1");
        ApiException invalidPage = assertThrows(
                ApiException.class,
                () -> service.save(
                        USER_ID,
                        SUB_CHAPTER_ID,
                        command("unknown", LearningProgressStatus.IN_PROGRESS)
                )
        );
        assertEquals(ErrorCode.INVALID_PAGE_ID, invalidPage.getErrorCode());
    }

    @Test
    void returnsNotStartedWithoutWritingWhenNoProgressExists() {
        when(learningProgressMapper.findByUserIdAndSubChapterId(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(null);
        when(contentVersionMapper.findCurrentPublishedLesson(SUB_CHAPTER_ID))
                .thenReturn(publishedLesson());

        LearningProgressStatusResult result = service.getStatus(
                USER_ID,
                SUB_CHAPTER_ID
        );

        assertEquals(LearningProgressStatus.NOT_STARTED,
                result.progress().getStatus());
        assertEquals(CONTENT_VERSION_ID,
                result.progress().getContentVersionId());
        assertNull(result.progress().getStartedAt());
        assertFalse(result.quizProgress().isCompleted());
        assertNull(result.quizProgress().getActiveAttemptId());
        verify(learningProgressMapper, never()).insertIfAbsent(any());
    }

    @Test
    void returnsCompletedAndActiveRetryQuizProgressSeparately() {
        LearningProgress progress = progress(
                LearningProgressStatus.COMPLETED,
                "page-3"
        );
        when(learningProgressMapper.findByUserIdAndSubChapterId(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(progress);
        when(quizAttemptMapper.findSubChapterQuizProgress(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(quizProgress(true, 3002L, 1, 3));

        LearningProgressStatusResult result = service.getStatus(
                USER_ID,
                SUB_CHAPTER_ID
        );

        assertTrue(result.quizProgress().isCompleted());
        assertEquals(3002L, result.quizProgress().getActiveAttemptId());
        assertEquals(1, result.quizProgress().getAnsweredCount());
        assertEquals(3, result.quizProgress().getTotalCount());
    }

    @Test
    void firstPutRejectsUnpublishedContent() {
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(null);
        when(contentVersionMapper.findCurrentPublishedLesson(SUB_CHAPTER_ID))
                .thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.save(
                        USER_ID,
                        SUB_CHAPTER_ID,
                        command(null, LearningProgressStatus.IN_PROGRESS)
                )
        );

        assertEquals(ErrorCode.CONTENT_NOT_PUBLISHED, exception.getErrorCode());
    }

    private void stubValidPage(String pageId) {
        when(contentVersionMapper.findById(CONTENT_VERSION_ID))
                .thenReturn(contentVersion());
        when(contentStorage.load(STORED_OBJECT)).thenReturn(new StoredContent(
                ("{\"schemaVersion\":\"1.0\",\"pages\":["
                        + "{\"id\":\"page-1\"},{\"id\":\"page-2\"},"
                        + "{\"id\":\"page-3\"}]}")
                        .getBytes(StandardCharsets.UTF_8),
                "application/json"
        ));
    }

    private SubChapterQuizProgress quizProgress(
            boolean completed,
            Long activeAttemptId,
            int answeredCount,
            int totalCount
    ) {
        SubChapterQuizProgress progress = new SubChapterQuizProgress();
        progress.setCompleted(completed);
        progress.setActiveAttemptId(activeAttemptId);
        progress.setAnsweredCount(answeredCount);
        progress.setTotalCount(totalCount);
        return progress;
    }

    private PublishedLessonReference publishedLesson() {
        PublishedLessonReference reference = new PublishedLessonReference();
        reference.setSubChapterId(SUB_CHAPTER_ID);
        reference.setContentVersionId(CONTENT_VERSION_ID);
        return reference;
    }

    private ContentVersion contentVersion() {
        ContentVersion version = new ContentVersion();
        version.setContentVersionId(CONTENT_VERSION_ID);
        version.setSubChapterId(SUB_CHAPTER_ID);
        version.setSchemaVersion("1.0");
        version.setStorageObjectKey(STORED_OBJECT.objectKey());
        version.setStorageVersionId(STORED_OBJECT.versionId());
        return version;
    }

    private SubChapter activeSubChapter() {
        SubChapter subChapter = new SubChapter();
        subChapter.setSubChapterId(SUB_CHAPTER_ID);
        subChapter.setActive(true);
        return subChapter;
    }

    private LearningProgressUpdateCommand command(
            String pageId,
            LearningProgressStatus status
    ) {
        return new LearningProgressUpdateCommand(
                CONTENT_VERSION_ID,
                pageId,
                status
        );
    }

    private LearningProgress progress(
            LearningProgressStatus status,
            String pageId
    ) {
        LearningProgress progress = new LearningProgress();
        progress.setProgressId(901L);
        progress.setUserId(USER_ID);
        progress.setSubChapterId(SUB_CHAPTER_ID);
        progress.setContentVersionId(CONTENT_VERSION_ID);
        progress.setLastPageId(pageId);
        progress.setStatus(status);
        progress.setStartedAt(LocalDateTime.of(2026, 8, 9, 3, 0));
        progress.setUpdatedAt(LocalDateTime.of(2026, 8, 9, 3, 10));
        return progress;
    }
}
