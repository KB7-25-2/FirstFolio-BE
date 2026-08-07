package org.firstfolio.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.portfolio.dto.request.PortfolioEventProcessRequest;
import org.firstfolio.portfolio.dto.response.PortfolioEventProcessResponse;
import org.firstfolio.portfolio.dto.response.PortfolioEventRetryResponse;
import org.firstfolio.portfolio.service.PortfolioEventService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 배치용 자산 이벤트 API (FUNC-041).
 *
 * <p>내부 호출 검증은 {@code ServletConfig}의 경로 인터셉터가 담당한다
 * ({@code /api/internal/**} → {@code X-Internal-Token}). 컨트롤러마다 반복하지 않는다.</p>
 *
 * <p>이 API는 <b>이미 만들어져 있는 예정 이벤트를 반영</b>할 뿐이다. 일정 자체는 사용자가
 * 가입·매수할 때 만들어진다. 그래서 배치가 죽어 있어도 이자가 사라지지 않고, 다시 돌리면
 * 밀린 것부터 순서대로 반영된다.</p>
 */
@RestController
@RequestMapping("/api/internal/portfolio-events")
@Tag(name = "내부 자산 이벤트", description = "내부 배치용 만기·이자·배당 이벤트 처리 API")
public class InternalPortfolioEventController {

    private final PortfolioEventService portfolioEventService;

    public InternalPortfolioEventController(PortfolioEventService portfolioEventService) {
        this.portfolioEventService = portfolioEventService;
    }

    @PostMapping("/process")
    @Operation(
            summary = "도래 자산 이벤트 처리",
            description = "process_until까지 도래한 예정 이벤트를 batch_size만큼 처리합니다. event_key와 멱등 키로 중복 반영을 방지하고, "
                    + "개별 실패는 다른 성공 건과 격리합니다. 현재 구현에서 next_cursor는 항상 null이며 필드는 생략하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "자산 이벤트 처리 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.PortfolioEventProcess.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "INVALID_REQUEST - process_until이 미래이거나 요청 형식이 올바르지 않음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "INTERNAL_CALL_REQUIRED - 내부 호출 토큰이 없거나 올바르지 않음"
                    )
            }
    )
    public ApiResponse<PortfolioEventProcessResponse> process(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "처리 기준 시각과 한 번에 처리할 최대 건수"
            )
            @RequestBody PortfolioEventProcessRequest request
    ) {
        return ApiResponse.of(new PortfolioEventProcessResponse(
                portfolioEventService.process(request.getProcessUntil(), request.getBatchSize())
        ));
    }

    /**
     * 실패한 이벤트를 같은 키로 다시 처리한다.
     *
     * <p>요청 본문이 없다. 재처리에 필요한 정보가 전부 이벤트에 들어 있어 <b>받을 것이 없다</b> —
     * 금액도 예정 시각도 가입 시점에 확정돼 저장돼 있다.</p>
     */
    @PostMapping("/{event_key}/retry")
    @Operation(
            summary = "실패 자산 이벤트 재처리",
            description = "FAILED 이벤트를 동일한 event_key로 다시 처리합니다. 재처리가 다시 실패하면 요청 자체는 200이고 status=FAILED, processed_at=null로 반환됩니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "자산 이벤트 재처리 요청 처리 완료",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.PortfolioEventRetry.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "INTERNAL_CALL_REQUIRED - 내부 호출 토큰이 없거나 올바르지 않음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404", description = "EVENT_NOT_FOUND - 이벤트를 찾을 수 없음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409", description = "EVENT_NOT_RETRYABLE - 재처리할 수 없는 상태"
                    )
            }
    )
    public ApiResponse<PortfolioEventRetryResponse> retry(
            @Parameter(
                    description = "재처리할 자산 이벤트 고유 키. 호출자는 내부 형식을 해석하지 않고 그대로 사용합니다.",
                    example = "interest-8101-8201-20260729T0300Z",
                    required = true
            )
            @PathVariable("event_key") String eventKey
    ) {
        return ApiResponse.of(new PortfolioEventRetryResponse(
                portfolioEventService.retry(eventKey)
        ));
    }
}
