package org.firstfolio.portfolio.controller;

import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.common.security.CurrentUserProvider;
import org.firstfolio.portfolio.dto.response.PortfolioDetailResponse;
import org.firstfolio.portfolio.dto.response.PortfolioTransactionPageResponse;
import org.firstfolio.portfolio.service.PortfolioQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자용 포트폴리오 API (FUNC-034).
 *
 * <p>경로가 {@code /current}인 것이 곧 권한 모델이다. 포트폴리오 식별자를 받지 않으므로
 * 남의 포트폴리오를 가리킬 방법이 없다.</p>
 */
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioQueryService queryService;
    private final CurrentUserProvider currentUserProvider;

    public PortfolioController(
            PortfolioQueryService queryService,
            CurrentUserProvider currentUserProvider
    ) {
        this.queryService = queryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/current")
    public ApiResponse<PortfolioDetailResponse> findCurrent() {
        long userId = currentUserProvider.require().userId();

        return ApiResponse.of(queryService.findCurrent(userId));
    }

    @GetMapping("/current/transactions")
    public ApiResponse<PortfolioTransactionPageResponse> findCurrentTransactions(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        long userId = currentUserProvider.require().userId();

        return ApiResponse.of(queryService.findCurrentTransactions(userId, type, cursor, size));
    }
}
