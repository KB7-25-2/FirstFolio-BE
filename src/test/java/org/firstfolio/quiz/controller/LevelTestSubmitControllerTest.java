package org.firstfolio.quiz.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.LevelTestChapterGradingResult;
import org.firstfolio.quiz.domain.LevelTestQuestionGradingResult;
import org.firstfolio.quiz.domain.LevelTestSubmitResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;
import org.firstfolio.quiz.service.LevelTestSubmitService;
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

class LevelTestSubmitControllerTest {

    private static final long USER_ID = 11L;
    private static final long ATTEMPT_ID = 2001L;

    private LevelTestSubmitService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LevelTestSubmitService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LevelTestSubmitController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void submitsAndReturnsQuestionChapterAndCurriculumResults() throws Exception {
        when(service.submit(USER_ID, ATTEMPT_ID)).thenReturn(result());

        mockMvc.perform(authenticated(post(
                        "/api/level-tests/attempts/2001/submit"
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempt_id").value(2001))
                .andExpect(jsonPath("$.data.status").value("GRADED"))
                .andExpect(jsonPath("$.data.question_results[0].question_id")
                        .value(1001))
                .andExpect(jsonPath("$.data.question_results[0].is_correct")
                        .value(false))
                .andExpect(jsonPath("$.data.chapter_results[0].total_count")
                        .value(2))
                .andExpect(jsonPath("$.data.chapter_results[0].correct_count")
                        .value(1))
                .andExpect(jsonPath("$.data.chapter_results[0].all_correct")
                        .value(false))
                .andExpect(jsonPath("$.data.recommendations[0].main_chapter_id")
                        .value(2))
                .andExpect(jsonPath("$.data.recommendations[0].source_type")
                        .value("LEVEL_TEST_WRONG"))
                .andExpect(jsonPath("$.data.cart_candidates[0].main_chapter_id")
                        .value(3))
                .andExpect(jsonPath("$.data.cart_candidates[0].asset_type")
                        .value("BOND"))
                .andExpect(jsonPath("$.data.correct_answer").doesNotExist())
                .andExpect(jsonPath("$.data.explanation").doesNotExist());

        verify(service).submit(USER_ID, ATTEMPT_ID);
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/level-tests/attempts/2001/submit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).submit(anyLong(), anyLong());
    }

    @Test
    void returnsMissingAnswersAndOwnershipErrors() throws Exception {
        when(service.submit(USER_ID, ATTEMPT_ID)).thenThrow(
                new ApiException(ErrorCode.REQUIRED_ANSWERS_MISSING)
        );
        mockMvc.perform(authenticated(post(
                        "/api/level-tests/attempts/2001/submit"
                )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("REQUIRED_ANSWERS_MISSING"));

        org.mockito.Mockito.reset(service);
        when(service.submit(USER_ID, ATTEMPT_ID)).thenThrow(
                new ApiException(ErrorCode.QUIZ_ATTEMPT_FORBIDDEN)
        );
        mockMvc.perform(authenticated(post(
                        "/api/level-tests/attempts/2001/submit"
                )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code")
                        .value("QUIZ_ATTEMPT_FORBIDDEN"));
    }

    private LevelTestSubmitResult result() {
        return new LevelTestSubmitResult(
                ATTEMPT_ID,
                QuizAttemptStatus.GRADED,
                List.of(
                        new LevelTestQuestionGradingResult(
                                1001L,
                                2L,
                                AssetType.DEPOSIT_SAVINGS,
                                false
                        ),
                        new LevelTestQuestionGradingResult(
                                1002L,
                                2L,
                                AssetType.DEPOSIT_SAVINGS,
                                true
                        ),
                        new LevelTestQuestionGradingResult(
                                1003L,
                                3L,
                                AssetType.BOND,
                                true
                        )
                ),
                List.of(
                        new LevelTestChapterGradingResult(
                                2L,
                                AssetType.DEPOSIT_SAVINGS,
                                2,
                                1,
                                false
                        ),
                        new LevelTestChapterGradingResult(
                                3L,
                                AssetType.BOND,
                                1,
                                1,
                                true
                        )
                )
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
