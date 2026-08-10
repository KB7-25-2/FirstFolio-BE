package org.firstfolio.simulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.simulation.dto.request.PriceRefreshRequest;
import org.firstfolio.simulation.dto.response.PriceRefreshResponse;
import org.firstfolio.simulation.service.PriceRefreshService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 스케줄러용 가격 갱신 API (FUNC-040).
 *
 * <p>내부 호출 검증은 {@code ServletConfig}의 경로 인터셉터가 담당한다
 * ({@code /api/internal/**} → {@code X-Internal-Token}). 컨트롤러마다 반복하지 않는다.</p>
 *
 * <p><b>폴링 주기는 이 서버가 정하지 않는다.</b> 스케줄러가 부르는 시점이 곧 기준 시점이고,
 * 서버는 요청받은 {@code reference_at}으로 가격을 쌓을 뿐이다. 주기 정책이 확정되면
 * 호출하는 쪽만 바꾸면 된다 (v3 7절 미정 항목).</p>
 */
@RestController
@RequestMapping("/api/internal/product-prices")
@Tag(name = "내부 상품 가격", description = "내부 스케줄러용 주식·펀드 모의 기준 가격 갱신 API")
public class InternalProductPriceController {

    private final PriceRefreshService priceRefreshService;

    public InternalProductPriceController(PriceRefreshService priceRefreshService) {
        this.priceRefreshService = priceRefreshService;
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "모의 기준 가격 갱신",
            description = "요청한 기준 시점으로 활성 주식·펀드의 가격을 새 행에 저장합니다. 동일 상품·기준 시점은 멱등하게 건너뜁니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "가격 갱신 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.PriceRefresh.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "INTERNAL_CALL_REQUIRED - 내부 호출 토큰이 없거나 올바르지 않음"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422", description = "PRICE_POLICY_INVALID - 가격 생성 정책 또는 상품 조건이 올바르지 않음"
                    )
            }
    )
    public ApiResponse<PriceRefreshResponse> refresh(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, description = "가격 기준 시점과 선택적인 상품 ID 목록"
            )
            @RequestBody PriceRefreshRequest request
    ) {
        return ApiResponse.of(new PriceRefreshResponse(
                priceRefreshService.refresh(request.getReferenceAt(), request.getProductIds())
        ));
    }
}
