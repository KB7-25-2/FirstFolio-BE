package org.firstfolio.simulation.controller;

import org.firstfolio.common.response.ApiResponse;
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
public class InternalProductPriceController {

    private final PriceRefreshService priceRefreshService;

    public InternalProductPriceController(PriceRefreshService priceRefreshService) {
        this.priceRefreshService = priceRefreshService;
    }

    @PostMapping("/refresh")
    public ApiResponse<PriceRefreshResponse> refresh(
            @RequestBody PriceRefreshRequest request
    ) {
        return ApiResponse.of(new PriceRefreshResponse(
                priceRefreshService.refresh(request.getReferenceAt(), request.getProductIds())
        ));
    }
}
