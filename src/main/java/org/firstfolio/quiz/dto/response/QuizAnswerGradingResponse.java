package org.firstfolio.quiz.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizAnswerGradingResult;
import org.firstfolio.quiz.domain.QuizGenerationType;

@Schema(description = "퀴즈 문항 즉시 채점 결과")
public record QuizAnswerGradingResponse(
        @Schema(description = "응시 ID", example = "3001") long attemptId,
        @Schema(description = "문항 버전 ID", example = "1001") long questionId,
        @Schema(description = "문항 생성 방식", example = "HUMAN") QuizGenerationType generationType,
        @Schema(description = "제출한 선택지 키", example = "B") String selectedKey,
        @Schema(description = "정답 여부", example = "false") boolean isCorrect,
        @Schema(description = "정답") QuizCorrectAnswerResponse correctAnswer,
        @Schema(description = "문항 해설") String explanation,
        @Schema(description = "응시 진행 상태") QuizAnswerAttemptResponse attempt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "마지막 문항 제출 시 포인트 지급 결과", nullable = true)
        QuizRewardResponse reward,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "퀴즈 완료 후 다음 동작", example = "NEXT_SUB_CHAPTER", nullable = true)
        String nextAction
) {
    public static QuizAnswerGradingResponse from(QuizAnswerGradingResult result) {
        return new QuizAnswerGradingResponse(
                result.attemptId(),
                result.questionId(),
                result.generationType(),
                result.selectedKey(),
                result.correct(),
                new QuizCorrectAnswerResponse(result.correctKey()),
                result.explanation(),
                new QuizAnswerAttemptResponse(
                        result.attemptStatus(),
                        result.answeredCount(),
                        result.totalCount(),
                        result.correctCount(),
                        result.score(),
                        result.attemptStatus() == org.firstfolio.quiz.domain.QuizAttemptStatus.GRADED
                ),
                result.reward() == null
                        ? null
                        : QuizRewardResponse.from(result.reward()),
                result.nextAction()
        );
    }
}
