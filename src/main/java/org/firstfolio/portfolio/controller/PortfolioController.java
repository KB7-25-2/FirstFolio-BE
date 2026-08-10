package org.firstfolio.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
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
@Tag(name = "포트폴리오", description = "현재 사용자의 모의 포트폴리오 조회·거래·초기화 API")
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
    @Operation(
            summary = "현재 포트폴리오 조회",
            description = "현재 활성 포트폴리오의 현금·보유 상품·평가액·손익·자산군 비중을 조회합니다. "
                    + "금액과 평가 기준은 서버가 확정하며, PRICE_UNAVAILABLE은 기준 가격을 구하지 못해 매입 원금으로 대체한 상태입니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "포트폴리오 상세 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.PortfolioDetail.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "ACTIVE_PORTFOLIO_NOT_FOUND - 활성 포트폴리오가 없음"
                    )
            }
    )
    public ApiResponse<PortfolioDetailResponse> findCurrent(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(queryService.findCurrent(currentUser.userId()));
    }

    @GetMapping("/current/transactions")
    @Operation(
            summary = "포트폴리오 거래 이력 조회",
            description = "현재 포트폴리오 세대의 거래와 예정·처리된 자산 이벤트 이력을 커서 방식으로 조회합니다. "
                    + "SCHEDULED 이벤트는 scheduled_at만 있고 processed_at은 null입니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "거래 이력 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.PortfolioTransactions.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "ACTIVE_PORTFOLIO_NOT_FOUND - 활성 포트폴리오가 없음"
                    )
            }
    )
    public ApiResponse<PortfolioTransactionPageResponse> findCurrentTransactions(
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(description = "거래 유형 필터", example = "INTEREST")
            @RequestParam(value = "type", required = false) String type,
            @Parameter(description = "다음 페이지 조회용 불투명 커서")
            @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(description = "페이지 크기", example = "20")
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
    @Operation(
            summary = "모의 상품 매수·매도",
            description = "매수는 금액, 주식·펀드 매도는 수량으로 요청합니다. 예·적금·채권 매도는 금액·수량 없이 전량 해지합니다. "
                    + "거래 단가와 최종 가능 여부는 서버가 결정하며 멱등 키로 중복 체결을 방지합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "포트폴리오 거래 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.Trade.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "PRODUCT_NOT_FOUND 또는 ACTIVE_PORTFOLIO_NOT_FOUND"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "IDEMPOTENCY_CONFLICT - 다른 요청 내용으로 사용한 중복 키"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422", description = "INSUFFICIENT_SIMULATION_CASH 또는 TRADE_NOT_ALLOWED - 잔액·수량·가격·거래시간·상품 조건 불충족"
                    )
            }
    )
    public ResponseEntity<ApiResponse<TradeResponse>> trade(
            @CurrentUser AuthenticatedUser currentUser,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "거래 멱등 키, BUY/SELL 구분, 상품 ID와 조건별 금액 또는 수량"
            )
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
    @Operation(
            summary = "포트폴리오 초기화",
            description = "고정 확인 문구를 검증한 뒤 현재 세대를 종료하고 모의투자금 30,000,000원의 새 포트폴리오 세대를 생성합니다. "
                    + "포인트·학습 진도·퀴즈 기록은 유지하고 초기화 이력을 남깁니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201", description = "포트폴리오 초기화 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.PortfolioReset.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "RESET_CONFIRMATION_REQUIRED - 확인 문구 불일치"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "429", description = "RESET_POLICY_LIMIT - 초기화 횟수 또는 대기 시간 정책 불충족"
                    )
            }
    )
    public ResponseEntity<ApiResponse<PortfolioResetResponse>> reset(
            @CurrentUser AuthenticatedUser currentUser,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "고정 확인 문구와 중복 초기화 방지 키"
            )
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
