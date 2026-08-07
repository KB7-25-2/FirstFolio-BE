package org.firstfolio.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.user.dto.response.PointBalanceResponse;
import org.firstfolio.user.service.PointBalanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@Tag(name = "포인트", description = "모의투자금과 분리된 보상 포인트 API")
public class PointController {

    private final PointBalanceService pointBalanceService;

    public PointController(PointBalanceService pointBalanceService) {
        this.pointBalanceService = pointBalanceService;
    }

    @GetMapping("/balance")
    @Operation(
            summary = "포인트 잔액 조회",
            description = "포인트 원장과 대조한 현재 보상 포인트 잔액을 조회합니다. 모의투자금과는 별개의 재화입니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "포인트 잔액 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.PointBalance.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401", description = "UNAUTHORIZED - 인증 필요"
                    )
            }
    )
    public ApiResponse<PointBalanceResponse> getBalance(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(PointBalanceResponse.from(
                pointBalanceService.get(currentUser.userId())
        ));
    }
}
