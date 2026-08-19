package org.firstfolio.quiz.integration.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.quiz.integration.ai.dto.response.QuizGenerationTargetResponse;
import org.firstfolio.quiz.integration.ai.service.QuizGenerationTargetQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 호출 검증은 {@code ServletConfig}의 경로 인터셉터가 담당한다
 * ({@code /api/internal/**} → {@code X-Internal-Token}). 컨트롤러마다 반복하지 않는다.
 */
@RestController
@RequestMapping("/api/internal/quiz-generation-targets")
@Tag(name = "AI 퀴즈 생성 대상", description = "AI 서버가 퀴즈 생성 전 조회하는 서비스 대상 대·소단원 API")
public class QuizGenerationTargetController {

    private final QuizGenerationTargetQueryService queryService;

    public QuizGenerationTargetController(QuizGenerationTargetQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(
            summary = "퀴즈 생성 대상 조회",
            description = "현재 서비스 대상으로 활성화된 전체 대단원과 각 대단원의 활성 소단원을 반환합니다. "
                    + "AI 서버는 이 응답의 단원 이름으로 생성 주제를 정하고, 단원 ID를 생성한 퀴즈에 직접 연결합니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "생성 대상 조회 성공"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "INTERNAL_CALL_REQUIRED - 내부 호출 토큰이 없거나 올바르지 않음"
                    )
            }
    )
    public ApiResponse<QuizGenerationTargetResponse> getTargets() {
        return ApiResponse.of(queryService.findTargets());
    }
}
