package org.firstfolio.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.dto.request.LessonContentUploadRequest;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.storage.LocalContentStorage;
import org.firstfolio.content.validation.LessonContentValidationService;
import org.firstfolio.content.validation.LessonSchemaValidator;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.quiz.domain.QuizQuestionReference;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentVersionLocalStorageIntegrationTest {

    private static final long SUB_CHAPTER_ID = 103L;
    private static final String VALID_LESSON_RESOURCE =
            "content/lesson/valid-lesson-1.0.json";

    @TempDir
    Path temporaryDirectory;

    @Test
    void uploadsValidatedLessonAndLoadsRegisteredLocalVersion() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode lesson = readLesson(objectMapper);
        SubChapterMapper subChapterMapper = mock(SubChapterMapper.class);
        QuizQuestionMapper quizQuestionMapper = mock(QuizQuestionMapper.class);
        ContentVersionMapper contentVersionMapper = mock(ContentVersionMapper.class);
        AdminAuditLogMapper auditLogMapper = mock(AdminAuditLogMapper.class);

        SubChapter subChapter = new SubChapter();
        subChapter.setSubChapterId(SUB_CHAPTER_ID);
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(subChapter);
        when(quizQuestionMapper.findReferencesByIds(List.of(1021L, 1022L, 1023L)))
                .thenReturn(List.of(
                        publishedQuestion(1021L),
                        publishedQuestion(1022L),
                        publishedQuestion(1023L)
                ));
        doAnswer(invocation -> {
            ContentVersion version = invocation.getArgument(0);
            version.setContentVersionId(302L);
            return 1;
        }).when(contentVersionMapper).insert(any(ContentVersion.class));

        LocalContentStorage storage = new LocalContentStorage(
                temporaryDirectory.resolve("content"),
                5L * 1024L * 1024L
        );
        LessonContentValidationService validationService =
                new LessonContentValidationService(
                        new LessonSchemaValidator(),
                        subChapterMapper,
                        quizQuestionMapper
                );
        ContentVersionService service = new ContentVersionService(
                validationService,
                storage,
                contentVersionMapper,
                subChapterMapper,
                auditLogMapper,
                Clock.systemUTC()
        );

        ContentVersion version = service.uploadLesson(
                SUB_CHAPTER_ID,
                new LessonContentUploadRequest(1, lesson),
                900L,
                "req-local-upload"
        );
        StoredContent stored = storage.load(new StoredObjectRef(
                version.getStorageObjectKey(),
                version.getStorageVersionId()
        ));

        assertEquals("application/json", stored.contentType());
        assertEquals(lesson, objectMapper.readTree(stored.content()));
        assertEquals(302L, version.getContentVersionId());
    }

    private JsonNode readLesson(ObjectMapper objectMapper) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(VALID_LESSON_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("테스트 강좌 JSON을 찾을 수 없습니다.");
            }
            return objectMapper.readTree(inputStream);
        }
    }

    private QuizQuestionReference publishedQuestion(long questionId) {
        return new QuizQuestionReference(
                questionId,
                QuizUsageType.SUB_CHAPTER,
                SUB_CHAPTER_ID,
                QuizQuestionStatus.PUBLISHED
        );
    }
}
