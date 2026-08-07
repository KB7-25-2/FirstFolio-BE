package org.firstfolio.content.validation;

import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.quiz.domain.QuizQuestionReference;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LessonContentValidationServiceTest {

    private static final long SUB_CHAPTER_ID = 103L;
    private static final String VALID_LESSON_RESOURCE =
            "content/lesson/valid-lesson-1.0.json";

    private SubChapterMapper subChapterMapper;
    private QuizQuestionMapper quizQuestionMapper;
    private LessonContentValidationService service;

    @BeforeEach
    void setUp() {
        subChapterMapper = mock(SubChapterMapper.class);
        quizQuestionMapper = mock(QuizQuestionMapper.class);
        service = new LessonContentValidationService(
                new LessonSchemaValidator(),
                subChapterMapper,
                quizQuestionMapper
        );
    }

    @Test
    void acceptsPublishedSubChapterQuestionsThatBelongToTarget() throws IOException {
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(subChapter());
        when(quizQuestionMapper.findReferencesByIds(List.of(1021L, 1022L, 1023L)))
                .thenReturn(List.of(
                        question(1023L, QuizUsageType.SUB_CHAPTER, SUB_CHAPTER_ID,
                                QuizQuestionStatus.PUBLISHED),
                        question(1021L, QuizUsageType.SUB_CHAPTER, SUB_CHAPTER_ID,
                                QuizQuestionStatus.PUBLISHED),
                        question(1022L, QuizUsageType.SUB_CHAPTER, SUB_CHAPTER_ID,
                                QuizQuestionStatus.PUBLISHED)
                ));

        LessonValidationResult result = service.validate(
                SUB_CHAPTER_ID,
                validLessonBytes()
        );

        assertTrue(result.isValid());
        verify(quizQuestionMapper).findReferencesByIds(List.of(1021L, 1022L, 1023L));
    }

    @Test
    void stopsBeforeDatabaseValidationWhenSchemaIsInvalid() {
        LessonValidationResult result = service.validate(
                SUB_CHAPTER_ID,
                "{}".getBytes(StandardCharsets.UTF_8)
        );

        assertFalse(result.isValid());
        verify(subChapterMapper, never()).findById(SUB_CHAPTER_ID);
        verify(quizQuestionMapper, never()).findReferencesByIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingTargetSubChapterBeforeQuestionLookup() throws IOException {
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(null);

        LessonValidationResult result = service.validate(
                SUB_CHAPTER_ID,
                validLessonBytes()
        );

        assertFalse(result.isValid());
        LessonValidationError error = result.errors().get(0);
        assertEquals(LessonValidationErrorCode.SUB_CHAPTER_NOT_FOUND, error.code());
        assertEquals("/subChapterId", error.path());
        verify(quizQuestionMapper, never()).findReferencesByIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reportsEveryInvalidQuestionReferenceAtItsJsonIndex() throws IOException {
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(subChapter());
        when(quizQuestionMapper.findReferencesByIds(List.of(1021L, 1022L, 1023L)))
                .thenReturn(List.of(
                        question(1021L, QuizUsageType.MAIN_CHAPTER, null,
                                QuizQuestionStatus.DRAFT),
                        question(1022L, QuizUsageType.SUB_CHAPTER, 999L,
                                QuizQuestionStatus.PUBLISHED)
                ));

        LessonValidationResult result = service.validate(
                SUB_CHAPTER_ID,
                validLessonBytes()
        );

        assertFalse(result.isValid());
        assertEquals(5, result.errors().size());
        assertError(
                result,
                LessonValidationErrorCode.QUESTION_NOT_PUBLISHED,
                "/subChapterQuiz/questionIds/0"
        );
        assertError(
                result,
                LessonValidationErrorCode.QUESTION_USAGE_TYPE_MISMATCH,
                "/subChapterQuiz/questionIds/0"
        );
        assertError(
                result,
                LessonValidationErrorCode.QUESTION_SUB_CHAPTER_MISMATCH,
                "/subChapterQuiz/questionIds/0"
        );
        assertError(
                result,
                LessonValidationErrorCode.QUESTION_SUB_CHAPTER_MISMATCH,
                "/subChapterQuiz/questionIds/1"
        );
        assertError(
                result,
                LessonValidationErrorCode.REFERENCED_QUESTION_NOT_FOUND,
                "/subChapterQuiz/questionIds/2"
        );
    }

    private byte[] validLessonBytes() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(VALID_LESSON_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("테스트 강좌 JSON을 찾을 수 없습니다.");
            }
            return inputStream.readAllBytes();
        }
    }

    private SubChapter subChapter() {
        SubChapter subChapter = new SubChapter();
        subChapter.setSubChapterId(SUB_CHAPTER_ID);
        return subChapter;
    }

    private QuizQuestionReference question(
            long questionId,
            QuizUsageType usageType,
            Long subChapterId,
            QuizQuestionStatus status
    ) {
        return new QuizQuestionReference(questionId, usageType, subChapterId, status);
    }

    private void assertError(
            LessonValidationResult result,
            LessonValidationErrorCode code,
            String path
    ) {
        assertTrue(result.errors().stream().anyMatch(error ->
                error.code() == code && error.path().equals(path)
        ));
    }
}
