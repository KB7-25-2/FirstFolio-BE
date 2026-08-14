package org.firstfolio.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionHistory;
import org.firstfolio.content.domain.ContentVersionStatus;
import org.firstfolio.content.domain.ContentWriteRequest;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.dto.request.LessonContentUploadRequest;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.validation.LessonContentValidationService;
import org.firstfolio.content.validation.LessonValidationError;
import org.firstfolio.content.validation.LessonValidationErrorCode;
import org.firstfolio.content.validation.LessonValidationResult;
import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentVersionServiceTest {

    private static final long SUB_CHAPTER_ID = 103L;
    private static final long ACTOR_ID = 900L;
    private static final String REQUEST_ID = "req-content-upload";
    private static final String OBJECT_KEY =
            "learning/sub-chapters/103/lesson.json";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 1, 0);
    private static final String VALID_LESSON_RESOURCE =
            "content/lesson/valid-lesson-1.0.json";

    private LessonContentValidationService validationService;
    private StaticContentStorage contentStorage;
    private ContentVersionMapper contentVersionMapper;
    private SubChapterMapper subChapterMapper;
    private AdminAuditLogMapper auditLogMapper;
    private ContentVersionService service;

    @BeforeEach
    void setUp() {
        validationService = mock(LessonContentValidationService.class);
        contentStorage = mock(StaticContentStorage.class);
        contentVersionMapper = mock(ContentVersionMapper.class);
        subChapterMapper = mock(SubChapterMapper.class);
        auditLogMapper = mock(AdminAuditLogMapper.class);
        service = new ContentVersionService(
                validationService,
                contentStorage,
                contentVersionMapper,
                subChapterMapper,
                auditLogMapper,
                Clock.fixed(Instant.parse("2026-08-07T01:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void validatesStoresAndRegistersDraftContentVersion() throws IOException {
        LessonContentUploadRequest request = uploadRequest();
        byte[] expectedContent = request.lesson().toString()
                .getBytes(StandardCharsets.UTF_8);
        when(validationService.validate(eq(SUB_CHAPTER_ID), any()))
                .thenReturn(LessonValidationResult.valid());
        when(contentStorage.store(any())).thenReturn(new StoredObjectRef(
                OBJECT_KEY,
                "storage-version-1"
        ));
        doAnswer(invocation -> {
            ContentVersion version = invocation.getArgument(0);
            version.setContentVersionId(302L);
            return 1;
        }).when(contentVersionMapper).insert(any(ContentVersion.class));

        ContentVersion created = service.uploadLesson(
                SUB_CHAPTER_ID,
                request,
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals(302L, created.getContentVersionId());
        assertEquals(SUB_CHAPTER_ID, created.getSubChapterId());
        assertEquals(2, created.getVersionNo());
        assertEquals("1.0", created.getSchemaVersion());
        assertEquals(OBJECT_KEY, created.getStorageObjectKey());
        assertEquals("storage-version-1", created.getStorageVersionId());
        assertEquals(ContentVersionStatus.DRAFT, created.getStatus());
        assertNull(created.getPublishedAt());
        assertEquals(ACTOR_ID, created.getCreatedBy());
        assertEquals(NOW, created.getCreatedAt());

        ArgumentCaptor<ContentWriteRequest> storageRequest =
                ArgumentCaptor.forClass(ContentWriteRequest.class);
        verify(contentStorage).store(storageRequest.capture());
        assertEquals(OBJECT_KEY, storageRequest.getValue().objectKey());
        assertEquals("application/json", storageRequest.getValue().contentType());
        assertArrayEquals(expectedContent, storageRequest.getValue().content());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("CREATE"),
                eq("CONTENT_VERSION"),
                eq(302L),
                isNull(),
                anyString(),
                eq(REQUEST_ID),
                eq(NOW)
        );
    }

    @Test
    void rejectsSchemaOrQuestionValidationFailureBeforeStorage() throws IOException {
        when(validationService.validate(eq(SUB_CHAPTER_ID), any()))
                .thenReturn(LessonValidationResult.invalid(new LessonValidationError(
                        LessonValidationErrorCode.QUESTION_NOT_PUBLISHED,
                        "/subChapterQuiz/questionIds/0",
                        "게시 상태가 아닌 퀴즈 문항입니다: 1021"
                )));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.uploadLesson(
                        SUB_CHAPTER_ID,
                        uploadRequest(),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(ErrorCode.CONTENT_VALIDATION_FAILED, exception.getErrorCode());
        verify(contentStorage, never()).store(any());
        verify(contentVersionMapper, never()).insert(any());
    }

    @Test
    void mapsMissingTargetSubChapterToNotFound() throws IOException {
        when(validationService.validate(eq(SUB_CHAPTER_ID), any()))
                .thenReturn(LessonValidationResult.invalid(new LessonValidationError(
                        LessonValidationErrorCode.SUB_CHAPTER_NOT_FOUND,
                        "/subChapterId",
                        "소단원이 없습니다."
                )));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.uploadLesson(
                        SUB_CHAPTER_ID,
                        uploadRequest(),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(ErrorCode.SUB_CHAPTER_NOT_FOUND, exception.getErrorCode());
        verify(contentStorage, never()).store(any());
    }

    @Test
    void rejectsExistingVersionNumberBeforeStorage() throws IOException {
        when(validationService.validate(eq(SUB_CHAPTER_ID), any()))
                .thenReturn(LessonValidationResult.valid());
        when(contentVersionMapper.countBySubChapterIdAndVersionNo(SUB_CHAPTER_ID, 2))
                .thenReturn(1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.uploadLesson(
                        SUB_CHAPTER_ID,
                        uploadRequest(),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(ErrorCode.CONTENT_VERSION_CONFLICT, exception.getErrorCode());
        verify(contentStorage, never()).store(any());
    }

    @Test
    void mapsConcurrentVersionInsertConflictAfterStorage() throws IOException {
        when(validationService.validate(eq(SUB_CHAPTER_ID), any()))
                .thenReturn(LessonValidationResult.valid());
        when(contentStorage.store(any())).thenReturn(new StoredObjectRef(
                OBJECT_KEY,
                "orphaned-storage-version"
        ));
        doThrow(new DuplicateKeyException("duplicate"))
                .when(contentVersionMapper).insert(any(ContentVersion.class));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.uploadLesson(
                        SUB_CHAPTER_ID,
                        uploadRequest(),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(ErrorCode.CONTENT_VERSION_CONFLICT, exception.getErrorCode());
        verify(contentStorage).store(any());
        verify(auditLogMapper, never()).insert(
                anyLong(), anyString(), anyString(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void rejectsMissingVersionOrLessonBeforeValidation() {
        ApiException missingVersion = assertThrows(
                ApiException.class,
                () -> service.uploadLesson(
                        SUB_CHAPTER_ID,
                        new LessonContentUploadRequest(null, new ObjectMapper().createObjectNode()),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );
        ApiException missingLesson = assertThrows(
                ApiException.class,
                () -> service.uploadLesson(
                        SUB_CHAPTER_ID,
                        new LessonContentUploadRequest(1, null),
                        ACTOR_ID,
                        REQUEST_ID
                )
        );

        assertEquals(ErrorCode.INVALID_REQUEST, missingVersion.getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, missingLesson.getErrorCode());
        verify(validationService, never()).validate(anyLong(), any());
    }

    @Test
    void returnsContentVersionHistoryWithCurrentVersion() {
        SubChapter subChapter = subChapter(301L);
        ContentVersion current = contentVersion(
                301L,
                1,
                ContentVersionStatus.PUBLISHED,
                NOW.minusDays(1)
        );
        ContentVersion draft = contentVersion(
                302L,
                2,
                ContentVersionStatus.DRAFT,
                null
        );
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(subChapter);
        when(contentVersionMapper.findAllBySubChapterId(SUB_CHAPTER_ID))
                .thenReturn(List.of(draft, current));

        ContentVersionHistory history = service.getContentVersions(SUB_CHAPTER_ID);

        assertEquals(2, history.versions().size());
        assertEquals(301L, history.currentContentVersionId());
        assertEquals(302L, history.versions().get(0).getContentVersionId());
    }

    @Test
    void rejectsContentVersionHistoryForMissingSubChapter() {
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getContentVersions(SUB_CHAPTER_ID)
        );

        assertEquals(ErrorCode.SUB_CHAPTER_NOT_FOUND, exception.getErrorCode());
        verify(contentVersionMapper, never()).findAllBySubChapterId(anyLong());
    }

    @Test
    void publishesDraftAndUpdatesCurrentVersion() {
        ContentVersion target = contentVersion(
                302L,
                2,
                ContentVersionStatus.DRAFT,
                null
        );
        SubChapter subChapter = subChapter(null);
        when(contentVersionMapper.findById(302L)).thenReturn(target);
        when(subChapterMapper.findByIdForUpdate(SUB_CHAPTER_ID)).thenReturn(subChapter);
        when(contentVersionMapper.findByIdForUpdate(302L)).thenReturn(target);
        when(contentVersionMapper.publishDraft(302L, NOW)).thenReturn(1);
        when(subChapterMapper.updateCurrentContentVersion(
                SUB_CHAPTER_ID,
                302L,
                NOW
        )).thenReturn(1);

        ContentVersion published = service.publishContentVersion(
                302L,
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals(ContentVersionStatus.PUBLISHED, published.getStatus());
        assertEquals(NOW, published.getPublishedAt());
        verify(contentVersionMapper, never()).retirePublished(anyLong());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("PUBLISH"),
                eq("CONTENT_VERSION"),
                eq(302L),
                anyString(),
                anyString(),
                eq(REQUEST_ID),
                eq(NOW)
        );
    }

    @Test
    void retiresPreviousVersionWhenPublishingNewDraft() {
        ContentVersion previous = contentVersion(
                301L,
                1,
                ContentVersionStatus.PUBLISHED,
                NOW.minusDays(1)
        );
        ContentVersion target = contentVersion(
                302L,
                2,
                ContentVersionStatus.DRAFT,
                null
        );
        SubChapter subChapter = subChapter(301L);
        when(contentVersionMapper.findById(302L)).thenReturn(target);
        when(subChapterMapper.findByIdForUpdate(SUB_CHAPTER_ID)).thenReturn(subChapter);
        when(contentVersionMapper.findByIdForUpdate(302L)).thenReturn(target);
        when(contentVersionMapper.findByIdForUpdate(301L)).thenReturn(previous);
        when(contentVersionMapper.retirePublished(301L)).thenReturn(1);
        when(contentVersionMapper.publishDraft(302L, NOW)).thenReturn(1);
        when(subChapterMapper.updateCurrentContentVersion(
                SUB_CHAPTER_ID,
                302L,
                NOW
        )).thenReturn(1);

        ContentVersion published = service.publishContentVersion(
                302L,
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals(ContentVersionStatus.RETIRED, previous.getStatus());
        assertEquals(ContentVersionStatus.PUBLISHED, published.getStatus());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("RETIRE"),
                eq("CONTENT_VERSION"),
                eq(301L),
                anyString(),
                anyString(),
                eq(REQUEST_ID),
                eq(NOW)
        );
        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("PUBLISH"),
                eq("CONTENT_VERSION"),
                eq(302L),
                anyString(),
                anyString(),
                eq(REQUEST_ID),
                eq(NOW)
        );
    }

    @Test
    void rejectsMissingOrNonDraftContentVersionForPublishing() {
        when(contentVersionMapper.findById(999L)).thenReturn(null);

        ApiException missing = assertThrows(
                ApiException.class,
                () -> service.publishContentVersion(999L, ACTOR_ID, REQUEST_ID)
        );
        assertEquals(ErrorCode.CONTENT_VERSION_NOT_FOUND, missing.getErrorCode());

        ContentVersion published = contentVersion(
                302L,
                2,
                ContentVersionStatus.PUBLISHED,
                NOW.minusDays(1)
        );
        when(contentVersionMapper.findById(302L)).thenReturn(published);
        when(subChapterMapper.findByIdForUpdate(SUB_CHAPTER_ID))
                .thenReturn(subChapter(302L));
        when(contentVersionMapper.findByIdForUpdate(302L)).thenReturn(published);

        ApiException notPublishable = assertThrows(
                ApiException.class,
                () -> service.publishContentVersion(302L, ACTOR_ID, REQUEST_ID)
        );
        assertEquals(ErrorCode.CONTENT_NOT_PUBLISHABLE, notPublishable.getErrorCode());
        verify(contentVersionMapper, never()).publishDraft(anyLong(), any());
        verify(subChapterMapper, never()).updateCurrentContentVersion(
                anyLong(), anyLong(), any()
        );
    }

    @Test
    void retiresCurrentPublishedVersionAndClearsCurrentConnection() {
        LocalDateTime publishedAt = NOW.minusDays(1);
        ContentVersion target = contentVersion(
                301L,
                1,
                ContentVersionStatus.PUBLISHED,
                publishedAt
        );
        when(contentVersionMapper.findById(301L)).thenReturn(target);
        when(subChapterMapper.findByIdForUpdate(SUB_CHAPTER_ID))
                .thenReturn(subChapter(301L));
        when(contentVersionMapper.findByIdForUpdate(301L)).thenReturn(target);
        when(contentVersionMapper.retirePublished(301L)).thenReturn(1);
        when(subChapterMapper.clearCurrentContentVersion(
                SUB_CHAPTER_ID,
                301L,
                NOW
        )).thenReturn(1);
        when(auditLogMapper.insert(
                anyLong(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), any()
        )).thenReturn(1);

        ContentVersion retired = service.retireContentVersion(
                301L,
                ACTOR_ID,
                REQUEST_ID
        );

        assertEquals(ContentVersionStatus.RETIRED, retired.getStatus());
        assertEquals(publishedAt, retired.getPublishedAt());
        verify(subChapterMapper).clearCurrentContentVersion(
                SUB_CHAPTER_ID,
                301L,
                NOW
        );
        verify(auditLogMapper).insert(
                eq(ACTOR_ID),
                eq("RETIRE"),
                eq("CONTENT_VERSION"),
                eq(301L),
                anyString(),
                anyString(),
                eq(REQUEST_ID),
                eq(NOW)
        );
    }

    @Test
    void rejectsRetiringVersionThatIsNotCurrentPublishedVersion() {
        ContentVersion published = contentVersion(
                301L,
                1,
                ContentVersionStatus.PUBLISHED,
                NOW.minusDays(1)
        );
        when(contentVersionMapper.findById(301L)).thenReturn(published);
        when(subChapterMapper.findByIdForUpdate(SUB_CHAPTER_ID))
                .thenReturn(subChapter(302L));
        when(contentVersionMapper.findByIdForUpdate(301L)).thenReturn(published);

        ApiException notCurrent = assertThrows(
                ApiException.class,
                () -> service.retireContentVersion(301L, ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.CONTENT_NOT_RETIRABLE, notCurrent.getErrorCode());
        verify(contentVersionMapper, never()).retirePublished(anyLong());
        verify(subChapterMapper, never()).clearCurrentContentVersion(
                anyLong(), anyLong(), any()
        );
    }

    @Test
    void rejectsRetiringDraftOrMissingContentVersion() {
        when(contentVersionMapper.findById(999L)).thenReturn(null);
        ApiException missing = assertThrows(
                ApiException.class,
                () -> service.retireContentVersion(999L, ACTOR_ID, REQUEST_ID)
        );
        assertEquals(ErrorCode.CONTENT_VERSION_NOT_FOUND, missing.getErrorCode());

        ContentVersion draft = contentVersion(
                302L,
                2,
                ContentVersionStatus.DRAFT,
                null
        );
        when(contentVersionMapper.findById(302L)).thenReturn(draft);
        when(subChapterMapper.findByIdForUpdate(SUB_CHAPTER_ID))
                .thenReturn(subChapter(302L));
        when(contentVersionMapper.findByIdForUpdate(302L)).thenReturn(draft);

        ApiException notRetirable = assertThrows(
                ApiException.class,
                () -> service.retireContentVersion(302L, ACTOR_ID, REQUEST_ID)
        );
        assertEquals(ErrorCode.CONTENT_NOT_RETIRABLE, notRetirable.getErrorCode());
    }

    private LessonContentUploadRequest uploadRequest() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(VALID_LESSON_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("테스트 강좌 JSON을 찾을 수 없습니다.");
            }
            JsonNode lesson = new ObjectMapper().readTree(inputStream);
            return new LessonContentUploadRequest(2, lesson);
        }
    }

    private SubChapter subChapter(Long currentContentVersionId) {
        SubChapter subChapter = new SubChapter();
        subChapter.setSubChapterId(SUB_CHAPTER_ID);
        subChapter.setCurrentContentVersionId(currentContentVersionId);
        return subChapter;
    }

    private ContentVersion contentVersion(
            long contentVersionId,
            int versionNo,
            ContentVersionStatus status,
            LocalDateTime publishedAt
    ) {
        ContentVersion version = ContentVersion.draft(
                SUB_CHAPTER_ID,
                versionNo,
                "1.0",
                new StoredObjectRef(OBJECT_KEY, "storage-version-" + versionNo),
                ACTOR_ID,
                NOW.minusDays(2)
        );
        version.setContentVersionId(contentVersionId);
        if (status == ContentVersionStatus.PUBLISHED) {
            version.publish(publishedAt);
        } else if (status == ContentVersionStatus.RETIRED) {
            version.publish(publishedAt);
            version.retire();
        } else {
            version.setStatus(status);
            version.setPublishedAt(publishedAt);
        }
        return version;
    }
}
