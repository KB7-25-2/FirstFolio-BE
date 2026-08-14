package org.firstfolio.quiz.integration.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionBatchRequest;
import org.firstfolio.quiz.integration.ai.dto.response.QuizQuestionBatchResponse;
import org.firstfolio.quiz.integration.ai.service.QuizQuestionBatchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 호출 검증은 {@code ServletConfig}의 경로 인터셉터가 담당한다
 * ({@code /api/internal/**} → {@code X-Internal-Token}). 컨트롤러마다 반복하지 않는다.
 */
@RestController
@RequestMapping("/api/internal/quiz-questions/batches")
@Tag(name = "AI 퀴즈 배치", description = "AI 서버가 생성한 퀴즈를 문제 풀에 저장하는 내부 API")
public class QuizQuestionBatchController {

    private final QuizQuestionBatchService batchService;

    public QuizQuestionBatchController(QuizQuestionBatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    @Operation(
            summary = "퀴즈 배치 저장",
            description = "AI가 생성하고 자동 검증까지 통과한 퀴즈를 최대 100건 배치로 받아 REVIEW 상태로 저장합니다. "
                    + "항목별 검증 실패는 REJECTED로 응답하며 다른 정상 항목 저장을 막지 않습니다.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200", description = "전체 성공, 부분 성공 또는 유효한 요청의 전체 항목 거절"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400", description = "INVALID_BATCH_REQUEST/BATCH_SIZE_EXCEEDED - 최상위 요청 오류"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "403", description = "INTERNAL_CALL_REQUIRED - 내부 호출 토큰이 없거나 올바르지 않음"
                    )
            }
    )
    public ApiResponse<QuizQuestionBatchResponse> receive(@RequestBody QuizQuestionBatchRequest request) {
        return ApiResponse.of(batchService.process(request));
    }
}
