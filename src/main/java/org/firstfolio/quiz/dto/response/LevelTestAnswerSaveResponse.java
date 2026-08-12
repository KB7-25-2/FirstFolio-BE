package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.LevelTestAnswerSaveResult;
import org.firstfolio.quiz.domain.QuizAttemptStatus;

import java.time.LocalDateTime;

@Schema(description = "레벨 테스트 답안 저장 결과")
public record LevelTestAnswerSaveResponse(
        @Schema(description = "응시 ID", example = "2001") long attemptId,
        @Schema(description = "이번 요청에서 저장한 문항 수", example = "2") int savedAnswerCount,
        @Schema(description = "현재 답안이 저장된 전체 문항 수", example = "5") int answeredCount,
        @Schema(description = "응시에 배정된 전체 문항 수", example = "8") int totalCount,
        @Schema(description = "응시 상태", example = "IN_PROGRESS") QuizAttemptStatus status,
        @Schema(description = "답안 저장 시각") LocalDateTime updatedAt
) {
    public static LevelTestAnswerSaveResponse from(
            LevelTestAnswerSaveResult result
    ) {
        return new LevelTestAnswerSaveResponse(
                result.attemptId(),
                result.savedAnswerCount(),
                result.answeredCount(),
                result.totalCount(),
                result.status(),
                result.updatedAt()
        );
    }
}
