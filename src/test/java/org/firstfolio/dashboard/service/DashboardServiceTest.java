package org.firstfolio.dashboard.service;

import org.firstfolio.dashboard.dto.response.DashboardResponse;
import org.firstfolio.dashboard.dto.response.UpcomingEventResponse;
import org.firstfolio.dashboard.mapper.DashboardMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LearningContinueResult;
import org.firstfolio.learning.service.LearningContinueService;
import org.firstfolio.portfolio.domain.AssetAllocation;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.PortfolioValuation;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.service.PortfolioValuationService;
import org.firstfolio.simulation.domain.AssetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final long USER_ID = 11L;
    private static final long PORTFOLIO_ID = 8001L;

    private PortfolioMapper portfolioMapper;
    private PortfolioValuationService portfolioValuationService;
    private LearningContinueService learningContinueService;
    private DashboardMapper dashboardMapper;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        portfolioMapper = mock(PortfolioMapper.class);
        portfolioValuationService = mock(PortfolioValuationService.class);
        learningContinueService = mock(LearningContinueService.class);
        dashboardMapper = mock(DashboardMapper.class);
        service = new DashboardService(
                portfolioMapper,
                portfolioValuationService,
                learningContinueService,
                dashboardMapper
        );
    }

    @Test
    void assemblesAllAvailableSectionsWhenPortfolioAndLearningExist() {
        Portfolio portfolio = portfolio();
        when(portfolioMapper.findActiveByUserId(USER_ID)).thenReturn(portfolio);
        when(portfolioValuationService.valuate(portfolio)).thenReturn(valuation());
        when(learningContinueService.getContinuePosition(USER_ID)).thenReturn(
                new LearningContinueResult(
                        502L, 2L, 101L, 301L, "page-2", 50,
                        "/learning/sub-chapters/101?page=page-2"
                )
        );
        when(dashboardMapper.findUpcomingEvents(PORTFOLIO_ID, 5))
                .thenReturn(List.of(upcomingTransaction()));

        DashboardResponse response = service.getDashboard(USER_ID);

        assertTrue(response.getPortfolio().isAvailable());
        assertEquals(0, new BigDecimal("31250000.00").compareTo(response.getPortfolio().getTotalAssets()));
        assertEquals(1, response.getPortfolio().getAllocation().size());
        assertEquals("STOCK", response.getPortfolio().getAllocation().get(0).getAssetType());

        assertTrue(response.getLearning().isAvailable());
        assertEquals(2L, response.getLearning().getMainChapterId());
        assertEquals(50, response.getLearning().getProgressPercent());

        assertEquals(1, response.getUpcomingEvents().size());
        UpcomingEventResponse event = response.getUpcomingEvents().get(0);
        assertEquals("MATURITY", event.getType());

        assertFalse(response.getDailyQuest().isAvailable());
        assertEquals("NOT_IMPLEMENTED", response.getDailyQuest().getReason());
        assertTrue(response.getLatestNews().isEmpty());
    }

    @Test
    void marksPortfolioAndUpcomingEventsUnavailableWhenNoActivePortfolio() {
        when(portfolioMapper.findActiveByUserId(USER_ID)).thenReturn(null);
        when(learningContinueService.getContinuePosition(USER_ID)).thenThrow(
                new ApiException(ErrorCode.CONTINUE_POSITION_NOT_FOUND)
        );

        DashboardResponse response = service.getDashboard(USER_ID);

        assertFalse(response.getPortfolio().isAvailable());
        assertEquals("NO_PORTFOLIO", response.getPortfolio().getReason());

        assertTrue(response.getUpcomingEvents().isEmpty());
        verify(dashboardMapper, never()).findUpcomingEvents(anyLong(), org.mockito.ArgumentMatchers.anyInt());

        assertFalse(response.getLearning().isAvailable());
        assertEquals("NOT_STARTED", response.getLearning().getReason());
    }

    private Portfolio portfolio() {
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(PORTFOLIO_ID);
        return portfolio;
    }

    private PortfolioValuation valuation() {
        return new PortfolioValuation(
                portfolio(),
                List.of(),
                List.of(new AssetAllocation(
                        AssetType.STOCK,
                        new BigDecimal("10080000.00"),
                        new BigDecimal("33.38")
                )),
                new BigDecimal("2000000.00"),
                new BigDecimal("10080000.00"),
                new BigDecimal("31250000.00"),
                new BigDecimal("1250000.00"),
                new BigDecimal("4.17"),
                LocalDateTime.now()
        );
    }

    private PortfolioTransaction upcomingTransaction() {
        PortfolioTransaction transaction = new PortfolioTransaction();
        transaction.setTransactionType(TransactionType.MATURITY);
        transaction.setScheduledAt(LocalDateTime.now().plusDays(3));
        return transaction;
    }
}
