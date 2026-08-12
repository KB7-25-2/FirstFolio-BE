package org.firstfolio.dashboard.controller;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.dashboard.dto.response.DailyQuestSummaryResponse;
import org.firstfolio.dashboard.dto.response.DashboardResponse;
import org.firstfolio.dashboard.dto.response.LatestNewsResponse;
import org.firstfolio.dashboard.dto.response.LearningSummaryResponse;
import org.firstfolio.dashboard.dto.response.PortfolioSummaryResponse;
import org.firstfolio.dashboard.dto.response.UpcomingEventResponse;
import org.firstfolio.dashboard.service.DashboardService;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private static final long USER_ID = 11L;

    private DashboardService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DashboardService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        ApiObjectMapperFactory.create()
                ))
                .build();
    }

    @Test
    void returnsDashboardForCurrentUser() throws Exception {
        when(service.getDashboard(USER_ID)).thenReturn(new DashboardResponse(
                new PortfolioSummaryResponse(
                        true, null,
                        new BigDecimal("31250000.00"),
                        new BigDecimal("1250000.00"),
                        new BigDecimal("4.17"),
                        List.of(new PortfolioSummaryResponse.Allocation(
                                "STOCK", new BigDecimal("33.38")
                        ))
                ),
                DailyQuestSummaryResponse.notImplemented(),
                new LearningSummaryResponse(true, null, 2L, 101L, 50),
                List.of(new UpcomingEventResponse("MATURITY", null)),
                List.of()
        ));

        mockMvc.perform(authenticated(get("/api/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolio.available").value(true))
                .andExpect(jsonPath("$.data.portfolio.total_assets").value("31250000.00"))
                .andExpect(jsonPath("$.data.learning.progress_percent").value(50))
                .andExpect(jsonPath("$.data.daily_quest.available").value(false))
                .andExpect(jsonPath("$.data.daily_quest.reason").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.data.upcoming_events[0].type").value("MATURITY"))
                .andExpect(jsonPath("$.data.latest_news").isArray());

        verify(service).getDashboard(USER_ID);
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(service, never()).getDashboard(anyLong());
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder builder
    ) {
        return builder.requestAttr(
                AuthenticationRequestAttributes.CURRENT_USER,
                new AuthenticatedUser(
                        USER_ID,
                        "firebase-uid",
                        "테스터",
                        UserRole.USER
                )
        );
    }
}
