package org.firstfolio.dailyquest.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.dailyquest.domain.DailyQuest;
import org.firstfolio.dailyquest.domain.DailyQuestQuestionView;
import org.firstfolio.dailyquest.domain.DailyQuestTodayResult;
import org.firstfolio.dailyquest.service.DailyQuestTodayQueryService;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizChoice;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyQuestControllerTest {

    private static final long USER_ID = 10L;

    private DailyQuestTodayQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DailyQuestTodayQueryService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DailyQuestController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void returnsTodayWithSavedAnswersAndNoGradingSecrets() throws Exception {
        when(service.getToday(USER_ID)).thenReturn(result());

        mockMvc.perform(authenticated(get("/api/daily-quests/today")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily_quest_id").value(4001))
                .andExpect(jsonPath("$.data.quest_date")
                        .value("2026-08-13"))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.answered_count").value(1))
                .andExpect(jsonPath("$.data.total_count").value(5))
                .andExpect(jsonPath("$.data.questions[0].daily_quest_item_id")
                        .value(5001))
                .andExpect(jsonPath("$.data.questions[0].question_id")
                        .value(1001))
                .andExpect(jsonPath("$.data.questions[0].display_order")
                        .value(1))
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
                .andExpect(jsonPath("$.data.questions[0].saved_answer.key")
                        .value("B"))
                .andExpect(jsonPath("$.data.questions[1].saved_answer")
                        .isEmpty())
                .andExpect(jsonPath("$.data.questions[0].correct_answer")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].correct_answer_json")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].explanation")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].source_refs_json")
                        .doesNotExist());

        verify(service).getToday(USER_ID);
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/daily-quests/today"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).getToday(anyLong());
    }

    @Test
    void returnsNotAvailableError() throws Exception {
        when(service.getToday(USER_ID)).thenThrow(new ApiException(
                ErrorCode.DAILY_QUEST_NOT_AVAILABLE
        ));

        mockMvc.perform(authenticated(get("/api/daily-quests/today")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code")
                        .value("DAILY_QUEST_NOT_AVAILABLE"));
    }

    @Test
    void returnsPoolUnavailableError() throws Exception {
        when(service.getToday(USER_ID)).thenThrow(new ApiException(
                ErrorCode.DAILY_QUEST_POOL_UNAVAILABLE
        ));

        mockMvc.perform(authenticated(get("/api/daily-quests/today")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("DAILY_QUEST_POOL_UNAVAILABLE"));
    }

    private DailyQuestTodayResult result() throws Exception {
        DailyQuest dailyQuest = DailyQuest.assigned(
                USER_ID,
                LocalDate.of(2026, 8, 13)
        );
        dailyQuest.setDailyQuestId(4001L);
        List<DailyQuestQuestionView> questions = java.util.stream.IntStream
                .rangeClosed(1, 5)
                .mapToObj(order -> new DailyQuestQuestionView(
                        5000L + order,
                        1000L + order,
                        order,
                        QuizQuestionType.SINGLE_CHOICE,
                        QuizGenerationType.HUMAN,
                        "예금 문제",
                        null,
                        List.of(
                                new QuizChoice("A", "선택지 A"),
                                new QuizChoice("B", "선택지 B")
                        ),
                        order == 1 ? "B" : null
                ))
                .toList();
        return new DailyQuestTodayResult(dailyQuest, 1, questions);
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
