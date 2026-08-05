package org.firstfolio.portfolio.controller;

import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
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
 *
 * <p>{@code @CurrentUser}가 붙은 파라미터는 인증 인터셉터가 요청에 넣어 둔 사용자로 채워진다.
 * 채울 사용자가 없으면 리졸버가 {@code UNAUTHORIZED}로 막아, 이 메서드 본문은 언제나
 * 인증된 요청에서만 실행된다.</p>
 */
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioQueryService queryService;

    public PortfolioController(PortfolioQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/current")
    public ApiResponse<PortfolioDetailResponse> findCurrent(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(queryService.findCurrent(currentUser.userId()));
    }

    @GetMapping("/current/transactions")
    public ApiResponse<PortfolioTransactionPageResponse> findCurrentTransactions(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(
                queryService.findCurrentTransactions(currentUser.userId(), type, cursor, size)
        );
    }
}
