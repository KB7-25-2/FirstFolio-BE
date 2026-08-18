package org.firstfolio.quiz.service;

import org.firstfolio.content.domain.ContentVersion;
import org.firstfolio.content.domain.ContentVersionStatus;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningProgress;
import org.firstfolio.learning.domain.LearningProgressStatus;
import org.firstfolio.learning.mapper.LearningProgressMapper;
import org.firstfolio.quiz.domain.QuizAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStartResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizAttemptMapper;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizAttemptStartServiceTest {

    private static final long USER_ID = 11L;
    private static final long SUB_CHAPTER_ID = 101L;
    private static final long CONTENT_VERSION_ID = 301L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 6, 0);

    private QuizAttemptMapper quizAttemptMapper;
    private QuizQuestionMapper quizQuestionMapper;
    private LearningProgressMapper learningProgressMapper;
    private ContentVersionMapper contentVersionMapper;
    private SubChapterMapper subChapterMapper;
    private StaticContentStorage contentStorage;
    private QuizAttemptStartService service;

    @BeforeEach
    void setUp() {
        quizAttemptMapper = mock(QuizAttemptMapper.class);
        quizQuestionMapper = mock(QuizQuestionMapper.class);
        learningProgressMapper = mock(LearningProgressMapper.class);
        contentVersionMapper = mock(ContentVersionMapper.class);
        subChapterMapper = mock(SubChapterMapper.class);
        contentStorage = mock(StaticContentStorage.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-10T06:00:00Z"),
                ZoneOffset.UTC
        );

        service = new QuizAttemptStartService(
                quizAttemptMapper,
                quizQuestionMapper,
                learningProgressMapper,
                contentVersionMapper,
                subChapterMapper,
                contentStorage,
                clock
        );
        when(subChapterMapper.findById(SUB_CHAPTER_ID))
                .thenReturn(activeSubChapter());
    }

    @Test
    void createsAttemptAndQuestionSnapshotsFromLearnedContentVersion() {
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(completedProgress());
        when(quizAttemptMapper.findInProgressByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(null);
        when(contentVersionMapper.findById(CONTENT_VERSION_ID))
                .thenReturn(contentVersion());
        when(contentStorage.load(any())).thenReturn(lessonContent());
        when(quizQuestionMapper.findAllByIds(List.of(1001L, 1002L)))
                .thenReturn(List.of(
                        question(1002L, QuizQuestionType.TRUE_FALSE),
                        question(1001L, QuizQuestionType.SINGLE_CHOICE)
                ));
        when(quizAttemptMapper.findMaxAttemptNoByUserIdAndSubChapterId(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(1);
        when(quizAttemptMapper.insertAttempt(any())).thenAnswer(invocation -> {
            QuizAttempt attempt = invocation.getArgument(0);
            attempt.setAttemptId(3001L);
            return 1;
        });
        when(quizAttemptMapper.insertAnswer(any())).thenReturn(1);

        QuizAttemptStartResult result = service.start(USER_ID, SUB_CHAPTER_ID);

        assertEquals(3001L, result.attempt().getAttemptId());
        assertEquals(QuizType.SUB_CHAPTER, result.attempt().getQuizType());
        assertEquals(2, result.attempt().getAttemptNo());
        assertEquals(QuizAttemptStatus.IN_PROGRESS, result.attempt().getStatus());
        assertEquals(2, result.attempt().getTotalCount());
        assertEquals(List.of(1001L, 1002L), result.questions().stream()
                .map(question -> question.questionId())
                .toList());
        assertEquals(List.of(1, 2), result.questions().stream()
                .map(question -> question.displayOrder())
                .toList());
        assertEquals("1", result.questions().get(0).choices().get(0).key());
        assertEquals("선택지 1", result.questions().get(0).choices().get(0).label());
        assertNull(result.questions().get(0).scenario());

        ArgumentCaptor<QuizAnswer> answerCaptor =
                ArgumentCaptor.forClass(QuizAnswer.class);
        verify(quizAttemptMapper, org.mockito.Mockito.times(2))
                .insertAnswer(answerCaptor.capture());
        QuizAnswer firstAnswer = answerCaptor.getAllValues().get(0);
        assertEquals(3001L, firstAnswer.getAttemptId());
        assertEquals(1001L, firstAnswer.getQuestionId());
        assertTrue(firstAnswer.getQuestionSnapshotJson().contains(
                "\"correct_answer_json\""
        ));
        assertTrue(firstAnswer.getQuestionSnapshotJson().contains(
                "\"explanation\""
        ));
        assertNull(firstAnswer.getUserAnswerJson());
        assertNull(firstAnswer.getCorrect());
        assertNull(firstAnswer.getAnsweredAt());
        assertEquals(NOW, firstAnswer.getCreatedAt());
    }

    @Test
    void restoresInProgressAttemptFromStoredSnapshots() {
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(completedProgress());
        QuizAttempt attempt = inProgressAttempt();
        when(quizAttemptMapper.findInProgressByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(attempt);
        when(quizAttemptMapper.findAnswersByAttemptId(3001L))
                .thenReturn(List.of(snapshotAnswer()));

        QuizAttemptStartResult result = service.start(USER_ID, SUB_CHAPTER_ID);

        assertEquals(3001L, result.attempt().getAttemptId());
        assertEquals(1, result.questions().size());
        assertEquals("복원된 문제", result.questions().get(0).prompt());
        assertEquals(QuizGenerationType.HUMAN,
                result.questions().get(0).generationType());
        verify(contentStorage, never()).load(any());
        verify(quizAttemptMapper, never()).insertAttempt(any());
        verify(quizAttemptMapper, never()).insertAnswer(any());
    }

    @Test
    void rejectsStartBeforeLessonCompletion() {
        LearningProgress progress = completedProgress();
        progress.setStatus(LearningProgressStatus.IN_PROGRESS);
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(progress);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.start(USER_ID, SUB_CHAPTER_ID)
        );

        assertEquals(ErrorCode.QUIZ_NOT_AVAILABLE, exception.getErrorCode());
        verify(quizAttemptMapper, never())
                .findInProgressByUserIdAndSubChapterIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void allowsRetiredQuestionPinnedByAlreadyPublishedLessonVersion() {
        arrangeNewAttempt();
        QuizQuestion retired = question(1001L, QuizQuestionType.SINGLE_CHOICE);
        retired.setStatus(QuizQuestionStatus.RETIRED);
        when(quizQuestionMapper.findAllByIds(List.of(1001L, 1002L)))
                .thenReturn(List.of(
                        retired,
                        question(1002L, QuizQuestionType.TRUE_FALSE)
                ));

        when(quizAttemptMapper.findMaxAttemptNoByUserIdAndSubChapterId(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(0);
        when(quizAttemptMapper.insertAttempt(any())).thenAnswer(invocation -> {
            QuizAttempt attempt = invocation.getArgument(0);
            attempt.setAttemptId(3001L);
            return 1;
        });
        when(quizAttemptMapper.insertAnswer(any())).thenReturn(1);

        QuizAttemptStartResult result = service.start(USER_ID, SUB_CHAPTER_ID);

        assertEquals(3001L, result.attempt().getAttemptId());
        assertEquals(List.of(1001L, 1002L), result.questions().stream()
                .map(question -> question.questionId())
                .toList());
        verify(quizAttemptMapper, org.mockito.Mockito.times(2)).insertAnswer(any());
    }

    @Test
    void rejectsInactiveSubChapterWithoutLookingUpProgress() {
        SubChapter inactive = activeSubChapter();
        inactive.setActive(false);
        when(subChapterMapper.findById(SUB_CHAPTER_ID)).thenReturn(inactive);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.start(USER_ID, SUB_CHAPTER_ID)
        );

        assertEquals(ErrorCode.QUIZ_NOT_AVAILABLE, exception.getErrorCode());
        verify(learningProgressMapper, never())
                .findByUserIdAndSubChapterIdForUpdate(anyLong(), anyLong());
    }

    private void arrangeNewAttempt() {
        when(learningProgressMapper.findByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(completedProgress());
        when(quizAttemptMapper.findInProgressByUserIdAndSubChapterIdForUpdate(
                USER_ID, SUB_CHAPTER_ID
        )).thenReturn(null);
        when(contentVersionMapper.findById(CONTENT_VERSION_ID))
                .thenReturn(contentVersion());
        when(contentStorage.load(any())).thenReturn(lessonContent());
    }

    private SubChapter activeSubChapter() {
        SubChapter chapter = new SubChapter();
        chapter.setSubChapterId(SUB_CHAPTER_ID);
        chapter.setActive(true);
        return chapter;
    }

    private LearningProgress completedProgress() {
        LearningProgress progress = new LearningProgress();
        progress.setProgressId(901L);
        progress.setUserId(USER_ID);
        progress.setSubChapterId(SUB_CHAPTER_ID);
        progress.setContentVersionId(CONTENT_VERSION_ID);
        progress.setStatus(LearningProgressStatus.COMPLETED);
        return progress;
    }

    private ContentVersion contentVersion() {
        ContentVersion version = new ContentVersion();
        version.setContentVersionId(CONTENT_VERSION_ID);
        version.setSubChapterId(SUB_CHAPTER_ID);
        version.setSchemaVersion("1.0");
        version.setStorageObjectKey("learning/sub-chapters/101/lesson.json");
        version.setStorageVersionId("version-301");
        version.setStatus(ContentVersionStatus.PUBLISHED);
        return version;
    }

    private StoredContent lessonContent() {
        return new StoredContent("""
                {
                  "schemaVersion": "1.0",
                  "subChapterQuiz": {
                    "questionIds": [1001, 1002]
                  }
                }
                """.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private QuizQuestion question(long id, QuizQuestionType type) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(id);
        question.setUsageType(QuizUsageType.SUB_CHAPTER);
        question.setSubChapterId(SUB_CHAPTER_ID);
        question.setQuestionType(type);
        question.setGenerationType(QuizGenerationType.HUMAN);
        question.setPrompt("문제 " + id);
        question.setScenarioJson(null);
        question.setOptionsJson("""
                [
                  {"key":"1","label":"선택지 1"},
                  {"key":"2","label":"선택지 2"}
                ]
                """);
        question.setCorrectAnswerJson("{\"key\":\"1\"}");
        question.setExplanation("정답 해설");
        question.setStatus(QuizQuestionStatus.PUBLISHED);
        return question;
    }

    private QuizAttempt inProgressAttempt() {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(3001L);
        attempt.setUserId(USER_ID);
        attempt.setQuizType(QuizType.SUB_CHAPTER);
        attempt.setSubChapterId(SUB_CHAPTER_ID);
        attempt.setContentVersionId(CONTENT_VERSION_ID);
        attempt.setAttemptNo(1);
        attempt.setStatus(QuizAttemptStatus.IN_PROGRESS);
        attempt.setTotalCount(1);
        attempt.setStartedAt(NOW);
        return attempt;
    }

    private QuizAnswer snapshotAnswer() {
        QuizAnswer answer = new QuizAnswer();
        answer.setQuizAnswerId(5001L);
        answer.setAttemptId(3001L);
        answer.setQuestionId(1001L);
        answer.setDisplayOrder(1);
        answer.setQuestionSnapshotJson("""
                {
                  "question_type": "SINGLE_CHOICE",
                  "generation_type": "HUMAN",
                  "prompt": "복원된 문제",
                  "scenario_json": null,
                  "options_json": [
                    {"key":"1","label":"선택지 1"},
                    {"key":"2","label":"선택지 2"}
                  ],
                  "correct_answer_json": {"key":"1"},
                  "explanation": "정답 해설"
                }
                """);
        return answer;
    }
}
