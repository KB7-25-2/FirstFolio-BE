package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.reward.domain.QuizRewardResult;

@Schema(description = "퀴즈 완료 포인트 지급 결과")
public record QuizRewardResponse(
        @Schema(description = "지급 포인트", example = "200") int points,
        @Schema(description = "포인트 원장 ID", example = "7001", nullable = true)
        Long pointTransactionId
) {
    public static QuizRewardResponse from(QuizRewardResult result) {
        return new QuizRewardResponse(
                result.points(),
                result.pointTransactionId()
        );
    }
}
