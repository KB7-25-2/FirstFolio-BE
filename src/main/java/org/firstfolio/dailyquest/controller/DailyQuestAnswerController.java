package org.firstfolio.dailyquest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.config.OpenApiResponseSchemas;
import org.firstfolio.dailyquest.dto.request.DailyQuestAnswerSaveRequest;
import org.firstfolio.dailyquest.dto.response.DailyQuestAnswerSaveResponse;
import org.firstfolio.dailyquest.service.DailyQuestAnswerSaveService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/daily-quests/today/items/{dailyQuestItemId}/answer"
)
@Tag(name = "일일 퀘스트", description = "사용자용 일일 퀘스트 API")
public class DailyQuestAnswerController {

    private final DailyQuestAnswerSaveService answerSaveService;

    public DailyQuestAnswerController(
            DailyQuestAnswerSaveService answerSaveService
    ) {
        this.answerSaveService = answerSaveService;
    }

    @PutMapping
    @Operation(
            summary = "일일 퀘스트 문항 답안 임시 저장",
            description = "오늘의 퀘스트에 배정된 문항 스냅샷으로 선택지를 검증한 뒤 "
                    + "답안을 채점 없이 저장합니다. 최종 제출 전에는 답안을 변경할 수 있고, "
                    + "같은 답안 재요청은 기존 저장 결과를 반환합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "답안 저장 또는 동일 답안 멱등 복원 성공",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = OpenApiResponseSchemas.DailyQuestAnswerSave.class
                                    )
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "404",
                            description = "DAILY_QUEST_ITEM_NOT_FOUND"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "DAILY_QUEST_ALREADY_COMPLETED"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "422",
                            description = "INVALID_ANSWER"
                    )
            }
    )
    public ApiResponse<DailyQuestAnswerSaveResponse> save(
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(
                    description = "오늘의 퀘스트에 배정된 문항 ID",
                    example = "5001",
                    required = true
            )
            @PathVariable long dailyQuestItemId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "임시 저장할 단일 답안. 예: {\"answer\":{\"key\":\"A\"}}"
            )
            @RequestBody(required = false) DailyQuestAnswerSaveRequest request
    ) {
        return ApiResponse.of(DailyQuestAnswerSaveResponse.from(
                answerSaveService.save(
                        currentUser.userId(),
                        dailyQuestItemId,
                        request == null ? null : request.selectedKey()
                )
        ));
    }
}
