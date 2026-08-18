package org.firstfolio.learning.service;

import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningContinueCandidate;
import org.firstfolio.learning.domain.LearningContinueResult;
import org.firstfolio.learning.domain.LearningContinueTarget;
import org.firstfolio.learning.domain.MainChapterQuizContinueCandidate;
import org.firstfolio.learning.mapper.LearningContinueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningContinueServiceTest {

    private static final long USER_ID = 11L;
    private static final long CONTENT_VERSION_ID = 301L;
    private static final StoredObjectRef STORED_OBJECT = new StoredObjectRef(
            "learning/sub-chapters/101/lesson.json",
            "storage-version-1"
    );

    private LearningContinueMapper learningContinueMapper;
    private ContentVersionMapper contentVersionMapper;
    private StaticContentStorage contentStorage;
    private LearningContinueService service;

    @BeforeEach
    void setUp() {
        learningContinueMapper = mock(LearningContinueMapper.class);
        contentVersionMapper = mock(ContentVersionMapper.class);
        contentStorage = mock(StaticContentStorage.class);
        service = new LearningContinueService(
                learningContinueMapper,
                contentVersionMapper,
                contentStorage
        );
    }

    @Test
    void returnsLatestPageAndCalculatedLessonProgress() {
        when(learningContinueMapper.findLatestInProgress(USER_ID))
                .thenReturn(candidate("page-2"));
        when(contentVersionMapper.findById(CONTENT_VERSION_ID))
                .thenReturn(publishedVersion());
        when(contentStorage.load(STORED_OBJECT)).thenReturn(lesson(
                "page-1", "page-2", "page-3", "page-4"
        ));

        LearningContinueResult result = service.getContinuePosition(USER_ID);

        assertEquals(LearningContinueTarget.LESSON, result.targetType());
        assertEquals(502L, result.curriculumItemId());
        assertEquals(2L, result.mainChapterId());
        assertEquals(101L, result.subChapterId());
        assertEquals(CONTENT_VERSION_ID, result.contentVersionId());
        assertEquals("page-2", result.lastPageId());
        assertEquals(50, result.progressPercent());
        assertEquals(
                "/learning/sub-chapters/101?page=page-2",
                result.route()
        );
    }

    @Test
    void returnsMainChapterQuizWhenThereIsNoInProgressLesson() {
        MainChapterQuizContinueCandidate candidate =
                new MainChapterQuizContinueCandidate();
        candidate.setCurriculumItemId(502L);
        candidate.setMainChapterId(2L);
        candidate.setAttemptId(3001L);
        when(learningContinueMapper.findMainChapterQuizCandidate(USER_ID))
                .thenReturn(candidate);

        LearningContinueResult result = service.getContinuePosition(USER_ID);

        assertEquals(LearningContinueTarget.MAIN_CHAPTER_QUIZ,
                result.targetType());
        assertEquals(502L, result.curriculumItemId());
        assertEquals(2L, result.mainChapterId());
        assertNull(result.subChapterId());
        assertNull(result.contentVersionId());
        assertEquals(3001L, result.attemptId());
        assertNull(result.lastPageId());
        assertEquals(100, result.progressPercent());
        assertEquals(
                "/learning/main-chapters/2/scenario-quiz",
                result.route()
        );
        verify(contentVersionMapper, never()).findById(CONTENT_VERSION_ID);
        verify(contentStorage, never()).load(STORED_OBJECT);
    }

    @Test
    void returnsLessonStartWhenNoPageWasSaved() {
        when(learningContinueMapper.findLatestInProgress(USER_ID))
                .thenReturn(candidate(null));
        when(contentVersionMapper.findById(CONTENT_VERSION_ID))
                .thenReturn(publishedVersion());
        when(contentStorage.load(STORED_OBJECT)).thenReturn(lesson(
                "page-1", "page-2"
        ));

        LearningContinueResult result = service.getContinuePosition(USER_ID);

        assertNull(result.lastPageId());
        assertEquals(0, result.progressPercent());
        assertEquals("/learning/sub-chapters/101", result.route());
    }

    @Test
    void returnsNotFoundWhenThereIsNoInProgressPosition() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getContinuePosition(USER_ID)
        );

        assertEquals(
                ErrorCode.CONTINUE_POSITION_NOT_FOUND,
                exception.getErrorCode()
        );
        verify(learningContinueMapper)
                .findMainChapterQuizCandidate(USER_ID);
        verify(contentVersionMapper, never()).findById(CONTENT_VERSION_ID);
    }

    @Test
    void rejectsInactiveOrChangedContentBeforeStorageLookup() {
        LearningContinueCandidate inactive = candidate("page-1");
        inactive.setSubChapterActive(false);
        when(learningContinueMapper.findLatestInProgress(USER_ID))
                .thenReturn(inactive);

        ApiException inactiveException = assertThrows(
                ApiException.class,
                () -> service.getContinuePosition(USER_ID)
        );
        assertEquals(ErrorCode.CONTENT_UNAVAILABLE,
                inactiveException.getErrorCode());

        LearningContinueCandidate changed = candidate("page-1");
        changed.setCurrentContentVersionId(302L);
        when(learningContinueMapper.findLatestInProgress(USER_ID))
                .thenReturn(changed);
        ApiException changedException = assertThrows(
                ApiException.class,
                () -> service.getContinuePosition(USER_ID)
        );
        assertEquals(ErrorCode.CONTENT_UNAVAILABLE,
                changedException.getErrorCode());
        verify(contentStorage, never()).load(STORED_OBJECT);
    }

    @Test
    void rejectsMissingPageAndStorageFailure() {
        when(learningContinueMapper.findLatestInProgress(USER_ID))
                .thenReturn(candidate("removed-page"));
        when(contentVersionMapper.findById(CONTENT_VERSION_ID))
                .thenReturn(publishedVersion());
        when(contentStorage.load(STORED_OBJECT))
                .thenReturn(lesson("page-1"));

        ApiException missingPage = assertThrows(
                ApiException.class,
                () -> service.getContinuePosition(USER_ID)
        );
        assertEquals(ErrorCode.CONTENT_UNAVAILABLE,
                missingPage.getErrorCode());

        when(contentStorage.load(STORED_OBJECT)).thenThrow(
                new ContentStorageException(
                        ContentStorageError.OBJECT_NOT_FOUND,
                        "missing"
                )
        );
        ApiException storageFailure = assertThrows(
                ApiException.class,
                () -> service.getContinuePosition(USER_ID)
        );
        assertEquals(ErrorCode.CONTENT_UNAVAILABLE,
                storageFailure.getErrorCode());
    }

    private LearningContinueCandidate candidate(String lastPageId) {
        LearningContinueCandidate candidate = new LearningContinueCandidate();
        candidate.setCurriculumItemId(502L);
        candidate.setMainChapterId(2L);
        candidate.setMainChapterActive(true);
        candidate.setSubChapterId(101L);
        candidate.setSubChapterActive(true);
        candidate.setCurrentContentVersionId(CONTENT_VERSION_ID);
        candidate.setContentVersionId(CONTENT_VERSION_ID);
        candidate.setLastPageId(lastPageId);
        return candidate;
    }

    private ContentVersion publishedVersion() {
        ContentVersion version = new ContentVersion();
        version.setContentVersionId(CONTENT_VERSION_ID);
        version.setSubChapterId(101L);
        version.setSchemaVersion("1.0");
        version.setStorageObjectKey(STORED_OBJECT.objectKey());
        version.setStorageVersionId(STORED_OBJECT.versionId());
        version.setStatus(ContentVersionStatus.PUBLISHED);
        return version;
    }

    private StoredContent lesson(String... pageIds) {
        StringBuilder pages = new StringBuilder();
        for (int index = 0; index < pageIds.length; index++) {
            if (index > 0) {
                pages.append(',');
            }
            pages.append("{\"id\":\"")
                    .append(pageIds[index])
                    .append("\"}");
        }
        String json = "{\"schemaVersion\":\"1.0\",\"pages\":["
                + pages + "]}";
        return new StoredContent(
                json.getBytes(StandardCharsets.UTF_8),
                "application/json"
        );
    }
}
