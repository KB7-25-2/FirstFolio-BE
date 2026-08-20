package org.firstfolio.dailyquest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.dailyquest.dto.response.DailyQuestLeaderboardResponse;
import org.firstfolio.dailyquest.service.DailyQuestLeaderboardQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-quests")
@Tag(name = "일일 퀘스트", description = "사용자용 일일 퀘스트 API")
public class DailyQuestLeaderboardController {

    private final DailyQuestLeaderboardQueryService queryService;

    public DailyQuestLeaderboardController(
            DailyQuestLeaderboardQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping("/leaderboard")
    @Operation(
            summary = "오늘의 일일 퀘스트 실시간 리더보드 조회",
            description = "한국 시간 기준 오늘 완료된 일일 퀘스트 점수로 "
                    + "공동 순위와 요청 사용자의 순위를 실시간 조회합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "실시간 리더보드 조회 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.DailyQuestLeaderboard.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400",
                            description = "INVALID_LEADERBOARD_PAGE"
                    )
            }
    )
    public ApiResponse<DailyQuestLeaderboardResponse> getToday(
            @CurrentUser AuthenticatedUser currentUser,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponse.of(queryService.getToday(
                currentUser.userId(),
                cursor,
                size
        ));
    }
}
