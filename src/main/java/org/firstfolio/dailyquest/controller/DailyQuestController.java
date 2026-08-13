package org.firstfolio.dailyquest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.dailyquest.dto.response.DailyQuestTodayResponse;
import org.firstfolio.dailyquest.service.DailyQuestTodayQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-quests")
@Tag(name = "일일 퀘스트", description = "사용자용 일일 퀘스트 API")
public class DailyQuestController {

    private final DailyQuestTodayQueryService queryService;

    public DailyQuestController(DailyQuestTodayQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/today")
    @Operation(
            summary = "오늘의 일일 퀘스트 조회",
            description = "오늘 퀘스트가 없으면 5문항을 멱등하게 배정합니다. "
                    + "기존 퀘스트는 고정된 문항 스냅샷과 저장 답안을 복원하며 "
                    + "정답, 해설과 내부 근거는 반환하지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "오늘의 퀘스트 조회 또는 최초 배정 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.DailyQuestToday.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403",
                            description = "DAILY_QUEST_NOT_AVAILABLE"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422",
                            description = "DAILY_QUEST_POOL_UNAVAILABLE"
                    )
            }
    )
    public ApiResponse<DailyQuestTodayResponse> getToday(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(DailyQuestTodayResponse.from(
                queryService.getToday(currentUser.userId())
        ));
    }
}
