package org.firstfolio.quiz.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizAnswerGradingResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.service.QuizAnswerGradingService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizAnswerControllerTest {

    private static final long USER_ID = 11L;
    private static final long ATTEMPT_ID = 3001L;
    private static final long QUESTION_ID = 1001L;

    private QuizAnswerGradingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(QuizAnswerGradingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuizAnswerController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void gradesOneQuestionAndReturnsImmediateFeedback() throws Exception {
        when(service.grade(USER_ID, ATTEMPT_ID, QUESTION_ID, "B"))
                .thenReturn(result());

        mockMvc.perform(authenticated(put(
                        "/api/learning/quiz-attempts/3001/answers/1001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answer": {
                                    "key": "B"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt_id").value(3001))
                .andExpect(jsonPath("$.data.question_id").value(1001))
                .andExpect(jsonPath("$.data.generation_type").value("HUMAN"))
                .andExpect(jsonPath("$.data.selected_key").value("B"))
                .andExpect(jsonPath("$.data.is_correct").value(false))
                .andExpect(jsonPath("$.data.correct_answer.key").value("C"))
                .andExpect(jsonPath("$.data.explanation").value("정기예금 해설"))
                .andExpect(jsonPath("$.data.attempt.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.attempt.answered_count").value(1))
                .andExpect(jsonPath("$.data.attempt.total_count").value(3))
                .andExpect(jsonPath("$.data.attempt.completed").value(false));

        verify(service).grade(USER_ID, ATTEMPT_ID, QUESTION_ID, "B");
    }

    @Test
    void rejectsPreviousSelectedKeyRequestShape() throws Exception {
        mockMvc.perform(authenticated(put(
                        "/api/learning/quiz-attempts/3001/answers/1001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selected_key\":\"B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verify(service, never()).grade(anyLong(), anyLong(), anyLong(), isNull());
    }

    @Test
    void returnsInvalidSelectedChoiceForMissingAnswerKey() throws Exception {
        when(service.grade(USER_ID, ATTEMPT_ID, QUESTION_ID, null))
                .thenThrow(new ApiException(ErrorCode.INVALID_SELECTED_CHOICE));

        mockMvc.perform(authenticated(put(
                        "/api/learning/quiz-attempts/3001/answers/1001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":{}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_SELECTED_CHOICE"));
    }

    @Test
    void returnsAnswerAlreadySubmittedConflict() throws Exception {
        when(service.grade(USER_ID, ATTEMPT_ID, QUESTION_ID, "C"))
                .thenThrow(new ApiException(ErrorCode.ANSWER_ALREADY_SUBMITTED));

        mockMvc.perform(authenticated(put(
                        "/api/learning/quiz-attempts/3001/answers/1001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":{\"key\":\"C\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("ANSWER_ALREADY_SUBMITTED"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(put(
                        "/api/learning/quiz-attempts/3001/answers/1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":{\"key\":\"B\"}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).grade(anyLong(), anyLong(), anyLong(), isNull());
    }

    private QuizAnswerGradingResult result() {
        return new QuizAnswerGradingResult(
                ATTEMPT_ID,
                QUESTION_ID,
                QuizGenerationType.HUMAN,
                "B",
                false,
                "C",
                "정기예금 해설",
                QuizAttemptStatus.IN_PROGRESS,
                1,
                3,
                false
        );
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder builder
    ) {
        return builder.requestAttr(
                AuthenticationRequestAttributes.CURRENT_USER,
                new AuthenticatedUser(
                        USER_ID,
                        "firebase-uid",
                        "학습자",
                        UserRole.USER
                )
        );
    }
}
