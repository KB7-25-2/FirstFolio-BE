package org.firstfolio.dailyquest.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.dailyquest.dto.response.DailyQuestLeaderboardResponse;
import org.firstfolio.dailyquest.service.DailyQuestLeaderboardQueryService;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyQuestLeaderboardControllerTest {

    private static final long USER_ID = 10L;

    private DailyQuestLeaderboardQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DailyQuestLeaderboardQueryService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DailyQuestLeaderboardController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void returnsTodayLeaderboardAndMyRank() throws Exception {
        when(service.getToday(USER_ID, null, 2)).thenReturn(response());

        mockMvc.perform(authenticated(
                        get("/api/daily-quests/leaderboard").param("size", "2")
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quest_date")
                        .value("2026-08-20"))
                .andExpect(jsonPath("$.data.calculated_at")
                        .value("2026-08-20T06:30:00Z"))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname")
                        .value("금융새싹"))
                .andExpect(jsonPath("$.data.items[0].score").value(5))
                .andExpect(jsonPath("$.data.my_rank.rank").value(7))
                .andExpect(jsonPath("$.data.my_rank.score").value(3))
                .andExpect(jsonPath("$.data.my_rank.nickname")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.next_cursor")
                        .value("opaque-cursor"));

        verify(service).getToday(USER_ID, null, 2);
    }

    @Test
    void returnsInvalidPageError() throws Exception {
        when(service.getToday(USER_ID, "invalid", null)).thenThrow(
                new ApiException(ErrorCode.INVALID_LEADERBOARD_PAGE)
        );

        mockMvc.perform(authenticated(
                        get("/api/daily-quests/leaderboard")
                                .param("cursor", "invalid")
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code")
                        .value("INVALID_LEADERBOARD_PAGE"));
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/daily-quests/leaderboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).getToday(anyLong(), any(), any());
    }

    private static DailyQuestLeaderboardResponse response() {
        return new DailyQuestLeaderboardResponse(
                LocalDate.of(2026, 8, 20),
                LocalDateTime.of(2026, 8, 20, 6, 30),
                List.of(new DailyQuestLeaderboardResponse.ItemResponse(
                        1,
                        "금융새싹",
                        5
                )),
                new DailyQuestLeaderboardResponse.MyRankResponse(7, 3),
                "opaque-cursor"
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
