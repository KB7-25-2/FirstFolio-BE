package org.firstfolio.quiz.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizDifficulty;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.dto.response.QuizQuestionPageResponse;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizQuestionQueryServiceTest {

    private QuizQuestionMapper mapper;
    private QuizQuestionQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(QuizQuestionMapper.class);
        service = new QuizQuestionQueryService(mapper);
    }

    @Test
    void returnsCursorOnlyWhenMorePagesExist() {
        List<QuizQuestion> found = new java.util.ArrayList<>();
        for (long id = 1001L; id <= 1021L; id++) {
            found.add(question(id));
        }

        when(mapper.findPage(any(), any(), any(), any(), any(), any(), eq(21)))
                .thenReturn(found);

        QuizQuestionPageResponse page = service.findPage(
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(20, page.items().size());
        assertEquals("1020", page.nextCursor());
    }

    @Test
    void returnsNullCursorOnLastPage() {
        when(mapper.findPage(any(), any(), any(), any(), any(), any(), eq(21)))
                .thenReturn(List.of(question(1001L)));

        QuizQuestionPageResponse page = service.findPage(
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(1, page.items().size());
        assertNull(page.nextCursor());
    }

    @Test
    void passesParsedFiltersToMapper() {
        when(mapper.findPage(any(), any(), any(), any(), any(), any(), eq(21)))
                .thenReturn(List.of());

        service.findPage(
                "sub_chapter",
                2L,
                101L,
                "published",
                " deposit-q-001 ",
                "1000"
        );

        ArgumentCaptor<QuizUsageType> usageTypeCaptor =
                ArgumentCaptor.forClass(QuizUsageType.class);
        ArgumentCaptor<QuizQuestionStatus> statusCaptor =
                ArgumentCaptor.forClass(QuizQuestionStatus.class);

        verify(mapper).findPage(
                usageTypeCaptor.capture(),
                eq(2L),
                eq(101L),
                statusCaptor.capture(),
                eq("deposit-q-001"),
                eq(1000L),
                eq(21)
        );
        assertEquals(QuizUsageType.SUB_CHAPTER, usageTypeCaptor.getValue());
        assertEquals(QuizQuestionStatus.PUBLISHED, statusCaptor.getValue());
    }

    @Test
    void rejectsInvalidFilterValues() {
        ApiException invalidUsageType = assertThrows(
                ApiException.class,
                () -> service.findPage("INVALID", null, null, null, null, null)
        );
        assertEquals(ErrorCode.INVALID_REQUEST, invalidUsageType.getErrorCode());

        ApiException invalidStatus = assertThrows(
                ApiException.class,
                () -> service.findPage(null, null, null, "READY", null, null)
        );
        assertEquals(ErrorCode.INVALID_REQUEST, invalidStatus.getErrorCode());

        ApiException invalidCursor = assertThrows(
                ApiException.class,
                () -> service.findPage(null, null, null, null, null, "abc")
        );
        assertEquals(ErrorCode.INVALID_REQUEST, invalidCursor.getErrorCode());
    }

    private QuizQuestion question(long questionId) {
        QuizQuestion question = QuizQuestion.draft(
                "deposit-q-001",
                1,
                QuizUsageType.SUB_CHAPTER,
                2L,
                101L,
                1,
                QuizQuestionType.SINGLE_CHOICE,
                QuizDifficulty.EASY,
                "질문",
                null,
                "[]",
                "{\"key\":\"1\"}",
                "해설",
                QuizGenerationType.HUMAN,
                null,
                null,
                900L,
                LocalDateTime.of(2026, 8, 10, 6, 0)
        );
        question.setQuestionId(questionId);
        return question;
    }
}
