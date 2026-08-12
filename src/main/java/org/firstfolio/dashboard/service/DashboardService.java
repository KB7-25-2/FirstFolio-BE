// service/DashboardService.java
package org.firstfolio.dashboard.service;

import org.firstfolio.dashboard.dto.response.DailyQuestSummaryResponse;
import org.firstfolio.dashboard.dto.response.DashboardResponse;
import org.firstfolio.dashboard.dto.response.LatestNewsResponse;
import org.firstfolio.dashboard.dto.response.LearningSummaryResponse;
import org.firstfolio.dashboard.dto.response.PortfolioSummaryResponse;
import org.firstfolio.dashboard.dto.response.UpcomingEventResponse;
import org.firstfolio.dashboard.mapper.DashboardMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.learning.domain.LearningContinueResult;
import org.firstfolio.learning.service.LearningContinueService;
import org.firstfolio.portfolio.domain.AssetAllocation;
import org.firstfolio.portfolio.domain.Portfolio;
import org.firstfolio.portfolio.domain.PortfolioTransaction;
import org.firstfolio.portfolio.domain.PortfolioValuation;
import org.firstfolio.portfolio.mapper.PortfolioMapper;
import org.firstfolio.portfolio.service.PortfolioValuationService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 대시보드 5개 섹션을 조립한다.
 *
 * <p>daily_quest·latest_news는 의존 도메인 코드가 없어 항상 NOT_IMPLEMENTED다.</p>
 *
 * <p>이 메서드 자체에는 {@code @Transactional}을 붙이지 않는다. learningContinueService가
 * 자기 트랜잭션 안에서 던지는 예외를 여기서 잡아 정상 흐름으로 바꾸는데, 같은 트랜잭션으로
 * 묶으면 그 예외가 트랜잭션을 rollback-only로 표시해 버려 이후 커밋 시점에
 * {@code UnexpectedRollbackException}이 난다. 각 하위 호출은 이미 스스로 세션을 관리한다.</p>
 */
@Service
public class DashboardService {

    private static final int UPCOMING_EVENTS_LIMIT = 5;

    private final PortfolioMapper portfolioMapper;
    private final PortfolioValuationService portfolioValuationService;
    private final LearningContinueService learningContinueService;
    private final DashboardMapper dashboardMapper;

    public DashboardService(
        PortfolioMapper portfolioMapper,
        PortfolioValuationService portfolioValuationService,
        LearningContinueService learningContinueService,
        DashboardMapper dashboardMapper
    ) {
        this.portfolioMapper = portfolioMapper;
        this.portfolioValuationService = portfolioValuationService;
        this.learningContinueService = learningContinueService;
        this.dashboardMapper = dashboardMapper;
    }

    public DashboardResponse getDashboard(long userId) {
        Portfolio portfolio = portfolioMapper.findActiveByUserId(userId);

        return new DashboardResponse(
            buildPortfolio(portfolio),
            DailyQuestSummaryResponse.notImplemented(),
            buildLearning(userId),
            buildUpcomingEvents(portfolio),
            List.of()
        );
    }

    private PortfolioSummaryResponse buildPortfolio(Portfolio portfolio) {
        if (portfolio == null) {
            return PortfolioSummaryResponse.unavailable("NO_PORTFOLIO");
        }

        PortfolioValuation valuation = portfolioValuationService.valuate(portfolio);

        List<PortfolioSummaryResponse.Allocation> allocation = valuation.getAllocations().stream()
            .map(this::toAllocation)
            .toList();

        return new PortfolioSummaryResponse(
            true,
            null,
            valuation.getTotalAssets(),
            valuation.getProfitLoss(),
            valuation.getProfitRate(),
            allocation
        );
    }

    private PortfolioSummaryResponse.Allocation toAllocation(AssetAllocation allocation) {
        return new PortfolioSummaryResponse.Allocation(
            allocation.getAssetType().name(),
            allocation.getRatio()
        );
    }

    private LearningSummaryResponse buildLearning(long userId) {
        try {
            LearningContinueResult result = learningContinueService.getContinuePosition(userId);
            return new LearningSummaryResponse(
                true,
                null,
                result.mainChapterId(),
                result.subChapterId(),
                result.progressPercent()
            );
        } catch (ApiException exception) {
            return LearningSummaryResponse.unavailable("NOT_STARTED");
        }
    }

    private List<UpcomingEventResponse> buildUpcomingEvents(Portfolio portfolio) {
        if (portfolio == null) {
            return List.of();
        }

        List<PortfolioTransaction> transactions = dashboardMapper.findUpcomingEvents(
            portfolio.getPortfolioId(), UPCOMING_EVENTS_LIMIT
        );

        return transactions.stream()
            .map(this::toEvent)
            .toList();
    }

    private UpcomingEventResponse toEvent(PortfolioTransaction transaction) {
        return new UpcomingEventResponse(
            transaction.getTransactionType().name(),
            transaction.getScheduledAt()
        );
    }
}
