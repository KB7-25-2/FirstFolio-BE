package org.firstfolio.quiz.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestAttemptQuestion;
import org.firstfolio.quiz.domain.LevelTestAttemptStartResult;
import org.firstfolio.quiz.domain.LevelTestSavedAnswer;
import org.firstfolio.quiz.domain.QuizAttempt;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.domain.QuizChoice;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizType;
import org.firstfolio.quiz.service.LevelTestAttemptStartService;
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

class LevelTestAttemptControllerTest {

    private static final long USER_ID = 11L;

    private LevelTestAttemptStartService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LevelTestAttemptStartService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LevelTestAttemptController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void startsOrRestoresIntegratedLevelTest() throws Exception {
        when(service.start(USER_ID)).thenReturn(result());

        mockMvc.perform(authenticated(post("/api/level-tests/attempts")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attempt_id").value(2001))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.question_count").value(1))
                .andExpect(jsonPath("$.data.questions[0].question_id")
                        .value(1001))
                .andExpect(jsonPath("$.data.questions[0].display_order")
                        .value(1))
                .andExpect(jsonPath(
                        "$.data.questions[0].main_chapter.main_chapter_id"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.questions[0].main_chapter.asset_type"
                ).value("DEPOSIT_SAVINGS"))
                .andExpect(jsonPath("$.data.questions[0].question_type")
                        .value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$.data.questions[0].generation_type")
                        .value("HUMAN"))
                .andExpect(jsonPath("$.data.questions[0].prompt")
                        .value("예금 문제"))
                .andExpect(jsonPath("$.data.questions[0].scenario").isEmpty())
                .andExpect(jsonPath("$.data.questions[0].choices[0].key")
                        .value("A"))
                .andExpect(jsonPath("$.data.questions[0].choices[0].label")
                        .value("선택지 A"))
                .andExpect(jsonPath("$.data.questions[0].correct_answer")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].explanation")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.answers[0].question_id")
                        .value(1001))
                .andExpect(jsonPath("$.data.answers[0].answer.key")
                        .value("B"));

        verify(service).start(USER_ID);
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/level-tests/attempts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).start(anyLong());
    }

    @Test
    void returnsAlreadyCompletedError() throws Exception {
        when(service.start(USER_ID)).thenThrow(new ApiException(
                ErrorCode.LEVEL_TEST_ALREADY_COMPLETED
        ));

        mockMvc.perform(authenticated(post("/api/level-tests/attempts")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("LEVEL_TEST_ALREADY_COMPLETED"));
    }

    @Test
    void returnsInvalidQuestionSetError() throws Exception {
        when(service.start(USER_ID)).thenThrow(new ApiException(
                ErrorCode.LEVEL_TEST_QUESTION_SET_INVALID
        ));

        mockMvc.perform(authenticated(post("/api/level-tests/attempts")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("LEVEL_TEST_QUESTION_SET_INVALID"));
    }

    private LevelTestAttemptStartResult result() {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(2001L);
        attempt.setUserId(USER_ID);
        attempt.setQuizType(QuizType.LEVEL_TEST);
        attempt.setAttemptNo(1);
        attempt.setStatus(QuizAttemptStatus.IN_PROGRESS);
        attempt.setTotalCount(1);

        LevelTestAttemptQuestion question = new LevelTestAttemptQuestion(
                1001L,
                1,
                2L,
                AssetType.DEPOSIT_SAVINGS,
                QuizQuestionType.SINGLE_CHOICE,
                QuizGenerationType.HUMAN,
                "예금 문제",
                null,
                List.of(
                        new QuizChoice("A", "선택지 A"),
                        new QuizChoice("B", "선택지 B")
                )
        );
        return new LevelTestAttemptStartResult(
                attempt,
                List.of(question),
                List.of(new LevelTestSavedAnswer(1001L, "B"))
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
