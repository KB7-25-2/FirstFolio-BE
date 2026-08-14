package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;

import java.time.LocalDateTime;

@Schema(description = "퀴즈 문항 버전 상태 변경 결과")
public record QuizQuestionStatusResponse(
        @Schema(description = "문항 버전 ID", example = "1201") long questionId,
        @Schema(description = "논리 문항 키", example = "deposit-basic-001") String questionKey,
        @Schema(description = "문항 버전 번호", example = "2") int versionNo,
        @Schema(description = "변경 후 상태", example = "PUBLISHED") QuizQuestionStatus status,
        @Schema(description = "최초 공개 시각", nullable = true, example = "2026-08-14T03:00:00Z")
        LocalDateTime publishedAt
) {

    public static QuizQuestionStatusResponse from(QuizQuestion question) {
        return new QuizQuestionStatusResponse(
                question.getQuestionId(),
                question.getQuestionKey(),
                question.getVersionNo(),
                question.getStatus(),
                question.getPublishedAt()
        );
    }
}
