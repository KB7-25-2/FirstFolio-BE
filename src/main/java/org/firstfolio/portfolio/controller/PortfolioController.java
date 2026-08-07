package org.firstfolio.portfolio.controller;

import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.portfolio.dto.request.PortfolioResetRequest;
import org.firstfolio.portfolio.dto.request.TradeRequest;
import org.firstfolio.portfolio.dto.response.PortfolioDetailResponse;
import org.firstfolio.portfolio.dto.response.PortfolioResetResponse;
import org.firstfolio.portfolio.dto.response.PortfolioTransactionPageResponse;
import org.firstfolio.portfolio.dto.response.TradeResponse;
import org.firstfolio.portfolio.domain.TransactionType;
import org.firstfolio.portfolio.service.PortfolioQueryService;
import org.firstfolio.portfolio.service.PortfolioResetService;
import org.firstfolio.portfolio.service.TradeCommand;
import org.firstfolio.portfolio.service.TradeService;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final PortfolioResetService resetService;
    private final TradeService tradeService;

    public PortfolioController(
            PortfolioQueryService queryService,
            PortfolioResetService resetService,
            TradeService tradeService
    ) {
        this.queryService = queryService;
        this.resetService = resetService;
        this.tradeService = tradeService;
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

    /**
     * 모의 상품을 매수·매도한다 (FUNC-035).
     *
     * <p><b>매수는 금액, 매도는 수량</b>이다. 예·적금·채권 매도는 전량 해지라 둘 다 보내지 않는다.
     * 잘못된 조합은 서비스가 {@code 422}로 거부한다 — 화면 검증과 별개로 서버도 확인한다.</p>
     */
    @PostMapping("/current/trades")
    public ResponseEntity<ApiResponse<TradeResponse>> trade(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestBody TradeRequest request
    ) {
        TradeCommand command = new TradeCommand(
                request.getIdempotencyKey(),
                parseTransactionType(request.getTransactionType()),
                request.getProductId(),
                request.getAmount(),
                request.getQuantity()
        );

        TradeResponse response = new TradeResponse(
                tradeService.trade(currentUser.userId(), command)
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    /** 거래로 쓸 수 있는 유형은 매수·매도뿐이다. 이자·배당 같은 값이 오면 거부한다. */
    private static TransactionType parseTransactionType(String transactionType) {
        if (transactionType == null || transactionType.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "거래 유형이 필요합니다.");
        }

        TransactionType parsed;

        try {
            parsed = TransactionType.valueOf(transactionType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "거래 유형이 올바르지 않습니다.");
        }

        if (parsed != TransactionType.BUY && parsed != TransactionType.SELL) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "매수 또는 매도만 요청할 수 있습니다.");
        }

        return parsed;
    }

    /**
     * 현재 세대를 닫고 3천만원짜리 새 세대를 만든다 (FUNC-037).
     *
     * <p>디자인의 "파산 신청"이 이 API다. 다만 <b>이 서비스에서는 파산이 성립하지 않아</b>
     * 초기화로만 다룬다 — 빚을 질 수단이 없고 예·적금·채권은 원금이 보장된다
     * ({@code DECISION_TIMELINE.md} D12).</p>
     */
    @PostMapping("/current/reset")
    public ResponseEntity<ApiResponse<PortfolioResetResponse>> reset(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestBody PortfolioResetRequest request
    ) {
        PortfolioResetResponse response = new PortfolioResetResponse(
                resetService.reset(
                        currentUser.userId(),
                        request.getConfirmation(),
                        request.getIdempotencyKey()
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
