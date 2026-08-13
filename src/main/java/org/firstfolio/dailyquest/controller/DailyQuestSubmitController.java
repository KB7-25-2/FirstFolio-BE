package org.firstfolio.dailyquest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.dailyquest.dto.response.DailyQuestSubmitResponse;
import org.firstfolio.dailyquest.service.DailyQuestSubmitService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-quests/today/submit")
@Tag(name = "일일 퀘스트", description = "사용자용 일일 퀘스트 API")
public class DailyQuestSubmitController {

    private final DailyQuestSubmitService submitService;

    public DailyQuestSubmitController(DailyQuestSubmitService submitService) {
        this.submitService = submitService;
    }

    @PostMapping
    @Operation(
            summary = "오늘의 일일 퀘스트 최종 제출",
            description = "임시 저장된 5개 답안을 배정 당시 문항 스냅샷으로 채점하고 "
                    + "정답 수에 따른 포인트를 한 번만 지급합니다. 요청 본문은 없습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "최초 제출 또는 기존 완료 결과 멱등 복원 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.DailyQuestSubmit.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "DAILY_QUEST_NOT_FOUND"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "DAILY_QUEST_INCOMPLETE"
                    )
            }
    )
    public ApiResponse<DailyQuestSubmitResponse> submit(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ApiResponse.of(DailyQuestSubmitResponse.from(
                submitService.submit(currentUser.userId())
        ));
    }
}
