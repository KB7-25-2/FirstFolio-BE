package org.firstfolio.learning.service;

import org.firstfolio.content.domain.PublishedLessonReference;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LessonContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonContentQueryServiceTest {

    private static final long SUB_CHAPTER_ID = 103L;
    private static final StoredObjectRef STORED_OBJECT = new StoredObjectRef(
            "learning/sub-chapters/103/lesson.json",
            "storage-version-2"
    );

    private ContentVersionMapper contentVersionMapper;
    private SubChapterMapper subChapterMapper;
    private StaticContentStorage contentStorage;
    private LessonContentQueryService service;

    @BeforeEach
    void setUp() {
        contentVersionMapper = mock(ContentVersionMapper.class);
        subChapterMapper = mock(SubChapterMapper.class);
        contentStorage = mock(StaticContentStorage.class);
        service = new LessonContentQueryService(
                contentVersionMapper,
                subChapterMapper,
                contentStorage
        );
    }

    @Test
    void loadsCurrentPublishedLessonFromExactStorageVersion() {
        PublishedLessonReference reference = publishedReference();
        when(contentVersionMapper.findCurrentPublishedLesson(SUB_CHAPTER_ID))
                .thenReturn(reference);
        when(contentStorage.load(STORED_OBJECT)).thenReturn(storedJson("""
                {
                  "schemaVersion": "1.0",
                  "pages": [],
                  "subChapterQuiz": {"questionIds": [1021]}
                }
                """));

        LessonContent content = service.getPublishedLesson(SUB_CHAPTER_ID);

        assertEquals(SUB_CHAPTER_ID, content.subChapterId());
        assertEquals("예금의 기초", content.title());
        assertEquals(302L, content.contentVersionId());
        assertEquals("1.0", content.schemaVersion());
        assertEquals("1.0", content.lesson().path("schemaVersion").textValue());
        verify(contentStorage).load(STORED_OBJECT);
        verify(subChapterMapper, never()).findById(SUB_CHAPTER_ID);
    }

    @Test
    void hidesMissingOrInactiveSubChapter() {
        when(contentVersionMapper.findCurrentPublishedLesson(SUB_CHAPTER_ID))
                .thenReturn(null);
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(null);

        ApiException missing = assertThrows(
                ApiException.class,
                () -> service.getPublishedLesson(SUB_CHAPTER_ID)
        );
        assertEquals(ErrorCode.SUB_CHAPTER_NOT_FOUND, missing.getErrorCode());

        SubChapter inactive = new SubChapter();
        inactive.setSubChapterId(SUB_CHAPTER_ID);
        inactive.setActive(false);
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(inactive);

        ApiException hidden = assertThrows(
                ApiException.class,
                () -> service.getPublishedLesson(SUB_CHAPTER_ID)
        );
        assertEquals(ErrorCode.SUB_CHAPTER_NOT_FOUND, hidden.getErrorCode());
        verify(contentStorage, never()).load(STORED_OBJECT);
    }

    @Test
    void rejectsActiveSubChapterWithoutPublishedContent() {
        when(contentVersionMapper.findCurrentPublishedLesson(SUB_CHAPTER_ID))
                .thenReturn(null);
        SubChapter active = new SubChapter();
        active.setSubChapterId(SUB_CHAPTER_ID);
        active.setActive(true);
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(active);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getPublishedLesson(SUB_CHAPTER_ID)
        );

        assertEquals(ErrorCode.CONTENT_NOT_PUBLISHED, exception.getErrorCode());
    }

    @Test
    void mapsStorageFailureToContentUnavailable() {
        when(contentVersionMapper.findCurrentPublishedLesson(SUB_CHAPTER_ID))
                .thenReturn(publishedReference());
        when(contentStorage.load(STORED_OBJECT)).thenThrow(new ContentStorageException(
                ContentStorageError.OBJECT_NOT_FOUND,
                "missing"
        ));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getPublishedLesson(SUB_CHAPTER_ID)
        );

        assertEquals(ErrorCode.CONTENT_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void rejectsInvalidMediaTypeOrSchemaVersion() {
        when(contentVersionMapper.findCurrentPublishedLesson(SUB_CHAPTER_ID))
                .thenReturn(publishedReference());
        when(contentStorage.load(STORED_OBJECT)).thenReturn(new StoredContent(
                "{}".getBytes(StandardCharsets.UTF_8),
                "text/plain"
        ));

        ApiException invalidMediaType = assertThrows(
                ApiException.class,
                () -> service.getPublishedLesson(SUB_CHAPTER_ID)
        );
        assertEquals(ErrorCode.CONTENT_UNAVAILABLE, invalidMediaType.getErrorCode());

        when(contentStorage.load(STORED_OBJECT)).thenReturn(storedJson("""
                {"schemaVersion": "2.0", "pages": []}
                """));
        ApiException schemaMismatch = assertThrows(
                ApiException.class,
                () -> service.getPublishedLesson(SUB_CHAPTER_ID)
        );
        assertEquals(ErrorCode.CONTENT_UNAVAILABLE, schemaMismatch.getErrorCode());
    }

    private PublishedLessonReference publishedReference() {
        PublishedLessonReference reference = new PublishedLessonReference();
        reference.setSubChapterId(SUB_CHAPTER_ID);
        reference.setTitle("예금의 기초");
        reference.setContentVersionId(302L);
        reference.setSchemaVersion("1.0");
        reference.setStorageObjectKey(STORED_OBJECT.objectKey());
        reference.setStorageVersionId(STORED_OBJECT.versionId());
        return reference;
    }

    private StoredContent storedJson(String json) {
        return new StoredContent(
                json.getBytes(StandardCharsets.UTF_8),
                "application/json; charset=UTF-8"
        );
    }
}
