package org.firstfolio.portfolio.controller;

import org.firstfolio.common.response.ApiResponse;
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
public class InternalPortfolioEventController {

    private final PortfolioEventService portfolioEventService;

    public InternalPortfolioEventController(PortfolioEventService portfolioEventService) {
        this.portfolioEventService = portfolioEventService;
    }

    @PostMapping("/process")
    public ApiResponse<PortfolioEventProcessResponse> process(
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
    public ApiResponse<PortfolioEventRetryResponse> retry(
            @PathVariable("event_key") String eventKey
    ) {
        return ApiResponse.of(new PortfolioEventRetryResponse(
                portfolioEventService.retry(eventKey)
        ));
    }
}
