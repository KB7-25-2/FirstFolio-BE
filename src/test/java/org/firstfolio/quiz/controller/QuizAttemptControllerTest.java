package org.firstfolio.quiz.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptQuestion;
import org.firstfolio.quiz.domain.QuizAttemptStartResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizChoice;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.service.QuizAttemptStartService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizAttemptControllerTest {

    private static final long USER_ID = 11L;
    private static final long SUB_CHAPTER_ID = 101L;

    private QuizAttemptStartService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(QuizAttemptStartService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QuizAttemptController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void startsSubChapterQuizWithoutExposingAnswerOrExplanation() throws Exception {
        when(service.start(USER_ID, SUB_CHAPTER_ID)).thenReturn(result());

        mockMvc.perform(authenticated(post(
                        "/api/learning/sub-chapters/101/quiz-attempts")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attempt_id").value(3001))
                .andExpect(jsonPath("$.data.quiz_type").value("SUB_CHAPTER"))
                .andExpect(jsonPath("$.data.main_chapter_id").doesNotExist())
                .andExpect(jsonPath("$.data.sub_chapter_id").value(101))
                .andExpect(jsonPath("$.data.content_version_id").value(301))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.question_count").value(1))
                .andExpect(jsonPath("$.data.questions[0].question_id").value(1001))
                .andExpect(jsonPath("$.data.questions[0].display_order").value(1))
                .andExpect(jsonPath("$.data.questions[0].question_type")
                        .value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.data.questions[0].generation_type")
                        .value("HUMAN"))
                .andExpect(jsonPath("$.data.questions[0].prompt")
                        .value("예금에 대한 설명으로 올바른 것은?"))
                .andExpect(jsonPath("$.data.questions[0].scenario").isEmpty())
                .andExpect(jsonPath("$.data.questions[0].choices[0].id")
                        .value("A"))
                .andExpect(jsonPath("$.data.questions[0].choices[0].text")
                        .value("선택지 A"))
                .andExpect(jsonPath("$.data.questions[0].correct_answer")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].explanation")
                        .doesNotExist());

        verify(service).start(USER_ID, SUB_CHAPTER_ID);
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post(
                        "/api/learning/sub-chapters/101/quiz-attempts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).start(anyLong(), anyLong());
    }

    @Test
    void returnsQuizNotAvailable() throws Exception {
        when(service.start(USER_ID, SUB_CHAPTER_ID))
                .thenThrow(new ApiException(ErrorCode.QUIZ_NOT_AVAILABLE));

        mockMvc.perform(authenticated(post(
                        "/api/learning/sub-chapters/101/quiz-attempts")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code")
                        .value("QUIZ_NOT_AVAILABLE"));
    }

    private QuizAttemptStartResult result() {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(3001L);
        attempt.setUserId(USER_ID);
        attempt.setQuizType(QuizType.SUB_CHAPTER);
        attempt.setSubChapterId(SUB_CHAPTER_ID);
        attempt.setContentVersionId(301L);
        attempt.setAttemptNo(1);
        attempt.setStatus(QuizAttemptStatus.IN_PROGRESS);
        attempt.setTotalCount(1);

        QuizAttemptQuestion question = new QuizAttemptQuestion(
                1001L,
                1,
                QuizQuestionType.SINGLE_CHOICE,
                QuizGenerationType.HUMAN,
                "예금에 대한 설명으로 올바른 것은?",
                null,
                List.of(
                        new QuizChoice("A", "선택지 A"),
                        new QuizChoice("B", "선택지 B")
                )
        );
        return new QuizAttemptStartResult(attempt, List.of(question));
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
