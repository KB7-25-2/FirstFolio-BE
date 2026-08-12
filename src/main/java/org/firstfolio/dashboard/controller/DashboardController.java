package org.firstfolio.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.dashboard.dto.response.DashboardResponse;
import org.firstfolio.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "대시보드", description = "홈 화면 집계 조회 API")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(
            summary = "대시보드 조회",
            description = "포트폴리오·학습·예정 자산 이벤트·일일 퀘스트·최신 뉴스 5개 섹션을 한 번에 반환합니다. "
                    + "섹션별로 조회 불가 사유가 있으면 해당 섹션만 available:false로 표시되고, 전체 응답은 항상 200입니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "대시보드 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.Dashboard.class
                                    )
                            )
                    )
            }
    )
    public ApiResponse<DashboardResponse> getDashboard(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(dashboardService.getDashboard(currentUser.userId()));
    }
}
