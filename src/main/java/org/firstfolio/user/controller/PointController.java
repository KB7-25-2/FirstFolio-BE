package org.firstfolio.user.controller;

import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.user.dto.response.PointBalanceResponse;
import org.firstfolio.user.service.PointBalanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
public class PointController {

    private final PointBalanceService pointBalanceService;

    public PointController(PointBalanceService pointBalanceService) {
        this.pointBalanceService = pointBalanceService;
    }

    @GetMapping("/balance")
    public ApiResponse<PointBalanceResponse> getBalance(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(PointBalanceResponse.from(
                pointBalanceService.get(currentUser.userId())
        ));
    }
}
