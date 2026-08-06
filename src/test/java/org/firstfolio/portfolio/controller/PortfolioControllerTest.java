package org.firstfolio.portfolio.controller;

import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.portfolio.dto.response.PortfolioDetailResponse;
import org.firstfolio.portfolio.dto.response.PortfolioTransactionPageResponse;
import org.firstfolio.portfolio.service.PortfolioQueryService;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 계층 검증. 서비스 테스트가 지나가는 구간을 본다.
 *
 * <ul>
 *   <li>{@code @CurrentUser}로 들어온 사용자가 서비스에 그대로 전달되는가</li>
 *   <li>인증되지 않은 요청이 컨트롤러 본문에 닿지 못하는가</li>
 *   <li>응답 표기가 API_DOCS와 같은가 (금액은 문자열, 비율은 숫자)</li>
 *   <li>오류가 {@code {"error": {...}}} 형태로 변환되는가</li>
 * </ul>
 */
class PortfolioControllerTest {

    private static final long USER_ID = 101L;

    private PortfolioQueryService queryService;
    private org.firstfolio.portfolio.service.PortfolioResetService resetService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(PortfolioQueryService.class);
        resetService = mock(org.firstfolio.portfolio.service.PortfolioResetService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PortfolioController(queryService, resetService))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
                )
                .addFilter(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    /** 인증 인터셉터가 통과시킨 뒤의 요청을 흉내낸다. */
    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(
                AuthenticationRequestAttributes.CURRENT_USER,
                new AuthenticatedUser(USER_ID, "firebase-uid-1", "테스터", UserRole.USER)
        );
    }

    private static PortfolioDetailResponse detail() {
        return new PortfolioDetailResponse(
                8001L,
                1,
                new BigDecimal("2000000.00"),
                List.of(),
                new PortfolioDetailResponse.Summary(
                        new BigDecimal("28200000.00"),
                        new BigDecimal("30200000.00"),
                        new BigDecimal("200000.00"),
                        new BigDecimal("0.67")
                ),
                List.of(new PortfolioDetailResponse.Allocation(
                        "DEPOSIT_SAVINGS",
                        new BigDecimal("10080000.00"),
                        new BigDecimal("33.38")
                )),
                LocalDateTime.of(2026, 7, 29, 3, 0)
        );
    }

    @Test
    @DisplayName("인증된 사용자의 포트폴리오를 조회한다")
    void returnsCurrentPortfolio() throws Exception {
        when(queryService.findCurrent(USER_ID)).thenReturn(detail());

        mockMvc.perform(authenticated(get("/api/portfolios/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolio_id").value(8001))
                .andExpect(jsonPath("$.data.cash_balance").value("2000000.00"))
                .andExpect(jsonPath("$.data.summary.total_assets").value("30200000.00"))
                .andExpect(jsonPath("$.data.allocation[0].ratio").value(33.38))
                .andExpect(jsonPath("$.data.valued_at").value("2026-07-29T03:00:00Z"));
    }

    @Test
    @DisplayName("요청한 사용자의 식별자로 조회한다 — 다른 사용자를 지정할 방법이 없다")
    void usesAuthenticatedUserId() throws Exception {
        when(queryService.findCurrent(USER_ID)).thenReturn(detail());

        mockMvc.perform(authenticated(get("/api/portfolios/current?user_id=999")))
                .andExpect(status().isOk());

        verify(queryService).findCurrent(USER_ID);
    }

    @Test
    @DisplayName("인증 정보가 없으면 401이고 서비스까지 가지 않는다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/portfolios/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(queryService, org.mockito.Mockito.never()).findCurrent(any());
    }

    @Test
    @DisplayName("활성 포트폴리오가 없으면 404 오류 응답으로 바뀐다")
    void translatesNotFoundToErrorResponse() throws Exception {
        when(queryService.findCurrent(USER_ID))
                .thenThrow(new ApiException(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/portfolios/current")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_PORTFOLIO_NOT_FOUND"));
    }

    @Test
    @DisplayName("이력 조회는 필터와 커서를 그대로 넘긴다")
    void passesTransactionQueryParameters() throws Exception {
        when(queryService.findCurrentTransactions(eq(USER_ID), any(), any(), any()))
                .thenReturn(new PortfolioTransactionPageResponse(List.of(), null));

        mockMvc.perform(authenticated(
                        get("/api/portfolios/current/transactions?type=INTEREST&cursor=8202&size=10")))
                .andExpect(status().isOk())
                // 마지막 페이지라도 필드를 생략하지 않는다 (API_DOCS가 null을 명시한다).
                // doesNotExist()는 필드가 없을 때도 통과해 버려 검증이 되지 않는다.
                .andExpect(content().string(containsString("\"next_cursor\":null")));

        verify(queryService).findCurrentTransactions(USER_ID, "INTEREST", "8202", 10);
    }

    @Test
    @DisplayName("이력 조회도 인증이 필요하다")
    void rejectsUnauthenticatedTransactionRequest() throws Exception {
        mockMvc.perform(get("/api/portfolios/current/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("초기화는 201로 닫힌 세대와 새 세대를 함께 돌려준다")
    void resetsPortfolio() throws Exception {
        when(resetService.reset(eq(USER_ID), any(), any())).thenReturn(
                new org.firstfolio.portfolio.service.PortfolioResetResult(
                        8001L, 8002L, 2, new BigDecimal("30000000.00"), 8299L
                )
        );

        mockMvc.perform(authenticated(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/portfolios/current/reset"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"RESET_PORTFOLIO\",\"idempotency_key\":\"reset-101-2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.closed_portfolio_id").value(8001))
                .andExpect(jsonPath("$.data.new_portfolio_id").value(8002))
                .andExpect(jsonPath("$.data.generation_no").value(2))
                .andExpect(jsonPath("$.data.cash_balance").value("30000000.00"))
                .andExpect(jsonPath("$.data.reset_transaction_id").value(8299));

        verify(resetService).reset(USER_ID, "RESET_PORTFOLIO", "reset-101-2");
    }

    @Test
    @DisplayName("확인 문구가 틀리면 400으로 나간다")
    void translatesWrongConfirmationToError() throws Exception {
        when(resetService.reset(eq(USER_ID), any(), any()))
                .thenThrow(new ApiException(ErrorCode.RESET_CONFIRMATION_REQUIRED));

        mockMvc.perform(authenticated(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/portfolios/current/reset"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"틀린문구\",\"idempotency_key\":\"reset-101-2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESET_CONFIRMATION_REQUIRED"));
    }

    @Test
    @DisplayName("초기화도 인증이 필요하다 — 남의 포트폴리오를 초기화할 수 없다")
    void rejectsUnauthenticatedReset() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/portfolios/current/reset")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"RESET_PORTFOLIO\",\"idempotency_key\":\"k\"}"))
                .andExpect(status().isUnauthorized());

        verify(resetService, org.mockito.Mockito.never()).reset(any(), any(), any());
    }
}
