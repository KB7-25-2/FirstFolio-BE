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
import org.firstfolio.quiz.service.MainChapterQuizAttemptStartService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MainChapterQuizAttemptControllerTest {

    private static final long USER_ID = 11L;
    private static final long MAIN_CHAPTER_ID = 10L;

    private MainChapterQuizAttemptStartService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MainChapterQuizAttemptStartService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MainChapterQuizAttemptController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void startsMainChapterQuizWithoutSubChapterScope() throws Exception {
        when(service.start(USER_ID, MAIN_CHAPTER_ID)).thenReturn(result());

        mockMvc.perform(authenticated(post(
                        "/api/learning/main-chapters/10/quiz-attempts")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attempt_id").value(4001))
                .andExpect(jsonPath("$.data.quiz_type").value("MAIN_CHAPTER"))
                .andExpect(jsonPath("$.data.main_chapter_id").value(10))
                .andExpect(jsonPath("$.data.sub_chapter_id").doesNotExist())
                .andExpect(jsonPath("$.data.content_version_id").doesNotExist())
                .andExpect(jsonPath("$.data.question_count").value(1))
                .andExpect(jsonPath("$.data.questions[0].question_type")
                        .value("SCENARIO"));
    }

    @Test
    void returnsSubChaptersIncomplete() throws Exception {
        when(service.start(USER_ID, MAIN_CHAPTER_ID)).thenThrow(
                new ApiException(ErrorCode.SUB_CHAPTERS_INCOMPLETE)
        );

        mockMvc.perform(authenticated(post(
                        "/api/learning/main-chapters/10/quiz-attempts")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code")
                        .value("SUB_CHAPTERS_INCOMPLETE"));
    }

    private QuizAttemptStartResult result() {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(4001L);
        attempt.setUserId(USER_ID);
        attempt.setQuizType(QuizType.MAIN_CHAPTER);
        attempt.setMainChapterId(MAIN_CHAPTER_ID);
        attempt.setAttemptNo(1);
        attempt.setStatus(QuizAttemptStatus.IN_PROGRESS);
        attempt.setTotalCount(1);
        return new QuizAttemptStartResult(
                attempt,
                List.of(new QuizAttemptQuestion(
                        2001L,
                        1,
                        QuizQuestionType.SCENARIO,
                        QuizGenerationType.HUMAN,
                        "금리 상승 시 채권 가격은?",
                        null,
                        List.of(new QuizChoice("A", "하락"))
                ))
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
