package org.firstfolio.dailyquest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.dailyquest.domain.DailyQuestItemGradingResult;
import org.firstfolio.dailyquest.domain.DailyQuestStatus;
import org.firstfolio.dailyquest.domain.DailyQuestSubmitResult;
import org.firstfolio.dailyquest.service.DailyQuestSubmitService;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.reward.domain.PointRewardResult;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyQuestSubmitControllerTest {

    private static final long USER_ID = 10L;

    private DailyQuestSubmitService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DailyQuestSubmitService.class);
        ObjectMapper objectMapper = ApiObjectMapperFactory.create();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DailyQuestSubmitController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    void submitsWithoutBodyAndReturnsGradingRewardAndSources()
            throws Exception {
        when(service.submit(USER_ID)).thenReturn(result(400, 9001L));

        mockMvc.perform(authenticated(request()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily_quest_id").value(4001))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.correct_count").value(4))
                .andExpect(jsonPath("$.data.score").value(4))
                .andExpect(jsonPath("$.data.reward.points").value(400))
                .andExpect(jsonPath(
                        "$.data.reward.point_transaction_id"
                ).value(9001))
                .andExpect(jsonPath(
                        "$.data.results[0].daily_quest_item_id"
                ).value(5001))
                .andExpect(jsonPath(
                        "$.data.results[0].submitted_answer.key"
                ).value("B"))
                .andExpect(jsonPath(
                        "$.data.results[0].correct_answer.key"
                ).value("A"))
                .andExpect(jsonPath(
                        "$.data.results[0].is_correct"
                ).value(false))
                .andExpect(jsonPath(
                        "$.data.results[0].source_refs"
                ).isEmpty())
                .andExpect(jsonPath(
                        "$.data.results[4].source_refs[0].url"
                ).value("https://example.com/news"))
                .andExpect(jsonPath("$.data.reward.policy_id")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.completed_at")
                        .value("2026-08-13T01:30:00Z"));

        verify(service).submit(USER_ID);
    }

    @Test
    void exposesNullLedgerForZeroPointResult() throws Exception {
        when(service.submit(USER_ID)).thenReturn(result(0, null));

        mockMvc.perform(authenticated(request()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reward.points").value(0))
                .andExpect(jsonPath(
                        "$.data.reward.point_transaction_id"
                ).isEmpty());
    }

    @Test
    void returnsTodayQuestNotFound() throws Exception {
        when(service.submit(USER_ID)).thenThrow(
                new ApiException(ErrorCode.DAILY_QUEST_NOT_FOUND)
        );

        mockMvc.perform(authenticated(request()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("DAILY_QUEST_NOT_FOUND"));
    }

    @Test
    void returnsIncompleteConflict() throws Exception {
        when(service.submit(USER_ID)).thenThrow(
                new ApiException(ErrorCode.DAILY_QUEST_INCOMPLETE)
        );

        mockMvc.perform(authenticated(request()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("DAILY_QUEST_INCOMPLETE"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(request())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).submit(anyLong());
    }

    private DailyQuestSubmitResult result(
            int points,
            Long pointTransactionId
    ) throws Exception {
        return new DailyQuestSubmitResult(
                4001L,
                DailyQuestStatus.COMPLETED,
                4,
                4,
                new PointRewardResult(91L, points, pointTransactionId),
                List.of(
                        gradingResult(5001L, false),
                        gradingResult(5002L, false),
                        gradingResult(5003L, false),
                        gradingResult(5004L, false),
                        gradingResult(5005L, true)
                ),
                LocalDateTime.of(2026, 8, 13, 1, 30)
        );
    }

    private DailyQuestItemGradingResult gradingResult(
            long itemId,
            boolean withSourceRefs
    )
            throws Exception {
        return new DailyQuestItemGradingResult(
                itemId,
                1000L + itemId,
                false,
                "B",
                "A",
                "정답 해설",
                withSourceRefs ? new ObjectMapper().readTree("""
                        [{
                          "title": "뉴스",
                          "url": "https://example.com/news",
                          "reference_at": "2026-08-13T00:00:00Z"
                        }]
                        """) : null
        );
    }

    private MockHttpServletRequestBuilder request() {
        return post("/api/daily-quests/today/submit");
    }

    private MockHttpServletRequestBuilder authenticated(
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
