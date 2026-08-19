package org.firstfolio.quiz.service;

import org.firstfolio.curriculum.domain.ChapterType;
import org.firstfolio.curriculum.domain.MainChapter;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.curriculum.mapper.MainChapterMapper;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizDifficulty;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.firstfolio.quiz.validation.QuizQuestionSchemaValidator;
import org.firstfolio.quiz.validation.QuizQuestionValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizQuestionPublicationServiceTest {

    private static final long ACTOR_ID = 900L;
    private static final String REQUEST_ID = "req-question-publication";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 6, 0);

    private QuizQuestionSchemaValidator schemaValidator;
    private QuizQuestionMapper questionMapper;
    private MainChapterMapper mainChapterMapper;
    private SubChapterMapper subChapterMapper;
    private AdminAuditLogMapper auditLogMapper;
    private QuizQuestionPublicationService service;

    @BeforeEach
    void setUp() {
        schemaValidator = mock(QuizQuestionSchemaValidator.class);
        questionMapper = mock(QuizQuestionMapper.class);
        mainChapterMapper = mock(MainChapterMapper.class);
        subChapterMapper = mock(SubChapterMapper.class);
        auditLogMapper = mock(AdminAuditLogMapper.class);
        service = new QuizQuestionPublicationService(
                schemaValidator,
                questionMapper,
                mainChapterMapper,
                subChapterMapper,
                auditLogMapper,
                Clock.fixed(Instant.parse("2026-08-14T06:00:00Z"), ZoneOffset.UTC)
        );

        when(schemaValidator.validate(any()))
                .thenReturn(QuizQuestionValidationResult.valid());
        when(subChapterMapper.findById(101L)).thenReturn(subChapter());
        when(mainChapterMapper.findById(2L)).thenReturn(mainChapter());
        when(auditLogMapper.insert(
                anyLong(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), any()
        )).thenReturn(1);
    }

    @Test
    void publishesLatestDraftAndRetiresPreviouslyPublishedVersion() {
        QuizQuestion previous = question(1201L, 1);
        previous.publish(LocalDateTime.of(2026, 8, 10, 6, 0));
        QuizQuestion target = question(1202L, 2);
        when(questionMapper.findById(1202L)).thenReturn(target);
        when(questionMapper.findLatestByQuestionKeyForUpdate(
                "deposit-basic-001"
        )).thenReturn(target);
        when(questionMapper.findPublishedByQuestionKeyForUpdate(
                "deposit-basic-001"
        )).thenReturn(List.of(previous));
        when(questionMapper.retirePublished(1201L)).thenReturn(1);
        when(questionMapper.publishDraft(1202L, NOW)).thenReturn(1);

        QuizQuestion result = service.publish(1202L, ACTOR_ID, REQUEST_ID);

        assertEquals(QuizQuestionStatus.RETIRED, previous.getStatus());
        assertEquals(QuizQuestionStatus.PUBLISHED, result.getStatus());
        assertEquals(NOW, result.getPublishedAt());
        verify(schemaValidator).validate(any());
        InOrder stateChanges = inOrder(questionMapper);
        stateChanges.verify(questionMapper).retirePublished(1201L);
        stateChanges.verify(questionMapper).publishDraft(1202L, NOW);
        verify(auditLogMapper).insert(
                eq(ACTOR_ID), eq("RETIRE"), eq("QUIZ_QUESTION"), eq(1201L),
                anyString(), anyString(), eq(REQUEST_ID), eq(NOW)
        );
        verify(auditLogMapper).insert(
                eq(ACTOR_ID), eq("PUBLISH"), eq("QUIZ_QUESTION"), eq(1202L),
                anyString(), anyString(), eq(REQUEST_ID), eq(NOW)
        );
    }

    @Test
    void publishesLatestReviewAndRetiresPreviouslyPublishedVersion() {
        QuizQuestion previous = question(1201L, 1);
        previous.publish(LocalDateTime.of(2026, 8, 10, 6, 0));
        QuizQuestion target = question(1202L, 2);
        target.setStatus(QuizQuestionStatus.REVIEW);
        when(questionMapper.findById(1202L)).thenReturn(target);
        when(questionMapper.findLatestByQuestionKeyForUpdate(
                "deposit-basic-001"
        )).thenReturn(target);
        when(questionMapper.findPublishedByQuestionKeyForUpdate(
                "deposit-basic-001"
        )).thenReturn(List.of(previous));
        when(questionMapper.retirePublished(1201L)).thenReturn(1);
        when(questionMapper.publishDraft(1202L, NOW)).thenReturn(1);

        QuizQuestion result = service.publish(1202L, ACTOR_ID, REQUEST_ID);

        assertEquals(QuizQuestionStatus.RETIRED, previous.getStatus());
        assertEquals(QuizQuestionStatus.PUBLISHED, result.getStatus());
        assertEquals(NOW, result.getPublishedAt());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID), eq("PUBLISH"), eq("QUIZ_QUESTION"), eq(1202L),
                anyString(), anyString(), eq(REQUEST_ID), eq(NOW)
        );
    }

    @Test
    void rejectsPublishingAlreadyPublishedQuestion() {
        QuizQuestion target = question(1201L, 1);
        target.publish(LocalDateTime.of(2026, 8, 10, 6, 0));
        when(questionMapper.findById(1201L)).thenReturn(target);
        when(questionMapper.findLatestByQuestionKeyForUpdate(
                "deposit-basic-001"
        )).thenReturn(target);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.publish(1201L, ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_NOT_PUBLISHABLE, exception.getErrorCode());
        verify(questionMapper, never()).publishDraft(anyLong(), any());
    }

    @Test
    void rejectsPublishingNonLatestDraft() {
        QuizQuestion requested = question(1201L, 1);
        QuizQuestion latest = question(1202L, 2);
        when(questionMapper.findById(1201L)).thenReturn(requested);
        when(questionMapper.findLatestByQuestionKeyForUpdate(
                "deposit-basic-001"
        )).thenReturn(latest);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.publish(1201L, ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_NOT_PUBLISHABLE, exception.getErrorCode());
        verify(schemaValidator, never()).validate(any());
        verify(questionMapper, never()).publishDraft(anyLong(), any());
        verify(auditLogMapper, never()).insert(
                anyLong(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString(), any()
        );
    }

    @Test
    void submitsDraftQuestionForReview() {
        QuizQuestion target = question(1201L, 1);
        when(questionMapper.findByIdForUpdate(1201L)).thenReturn(target);
        when(questionMapper.submitForReview(1201L)).thenReturn(1);

        QuizQuestion result = service.submitForReview(1201L, ACTOR_ID, REQUEST_ID);

        assertEquals(QuizQuestionStatus.REVIEW, result.getStatus());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID), eq("SUBMIT_REVIEW"), eq("QUIZ_QUESTION"), eq(1201L),
                anyString(), anyString(), eq(REQUEST_ID), eq(NOW)
        );
    }

    @Test
    void rejectsSubmittingNonDraftQuestionForReview() {
        QuizQuestion target = question(1201L, 1);
        target.setStatus(QuizQuestionStatus.REVIEW);
        when(questionMapper.findByIdForUpdate(1201L)).thenReturn(target);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.submitForReview(1201L, ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_NOT_REVIEWABLE, exception.getErrorCode());
        verify(questionMapper, never()).submitForReview(anyLong());
    }

    @Test
    void manuallyRetiresPublishedQuestionAndKeepsPublishedTimestamp() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 10, 6, 0);
        QuizQuestion target = question(1201L, 1);
        target.publish(publishedAt);
        when(questionMapper.findByIdForUpdate(1201L)).thenReturn(target);
        when(questionMapper.retirePublished(1201L)).thenReturn(1);

        QuizQuestion result = service.retire(1201L, ACTOR_ID, REQUEST_ID);

        assertEquals(QuizQuestionStatus.RETIRED, result.getStatus());
        assertEquals(publishedAt, result.getPublishedAt());
        verify(auditLogMapper).insert(
                eq(ACTOR_ID), eq("RETIRE"), eq("QUIZ_QUESTION"), eq(1201L),
                anyString(), anyString(), eq(REQUEST_ID), eq(NOW)
        );
    }

    @Test
    void rejectsRetiringQuestionThatIsNotPublished() {
        QuizQuestion target = question(1201L, 1);
        when(questionMapper.findByIdForUpdate(1201L)).thenReturn(target);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.retire(1201L, ACTOR_ID, REQUEST_ID)
        );

        assertEquals(ErrorCode.QUESTION_NOT_RETIRABLE, exception.getErrorCode());
        verify(questionMapper, never()).retirePublished(anyLong());
    }

    private QuizQuestion question(long questionId, int versionNo) {
        QuizQuestion question = QuizQuestion.draft(
                "deposit-basic-001",
                versionNo,
                QuizUsageType.SUB_CHAPTER,
                2L,
                101L,
                1,
                QuizQuestionType.SINGLE_CHOICE,
                QuizDifficulty.EASY,
                "예금의 특징으로 적절한 것은?",
                null,
                "[{\"key\":\"1\",\"label\":\"선택지 1\"},{\"key\":\"2\",\"label\":\"선택지 2\"}]",
                "{\"key\":\"1\"}",
                "정답 해설",
                QuizGenerationType.HUMAN,
                null,
                null,
                ACTOR_ID,
                LocalDateTime.of(2026, 8, 10, 6, 0)
        );
        question.setQuestionId(questionId);
        return question;
    }

    private SubChapter subChapter() {
        SubChapter subChapter = new SubChapter();
        subChapter.setSubChapterId(101L);
        subChapter.setMainChapterId(2L);
        return subChapter;
    }

    private MainChapter mainChapter() {
        MainChapter mainChapter = new MainChapter();
        mainChapter.setMainChapterId(2L);
        mainChapter.setChapterType(ChapterType.ASSET);
        return mainChapter;
    }
}
