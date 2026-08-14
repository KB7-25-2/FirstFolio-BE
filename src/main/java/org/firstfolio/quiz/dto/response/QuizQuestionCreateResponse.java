package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;

@Schema(description = "퀴즈 문항 버전 생성 결과")
public record QuizQuestionCreateResponse(
        @Schema(description = "생성된 문항 버전 ID", example = "1201") long questionId,
        @Schema(description = "논리 문항 키", example = "deposit-basic-001") String questionKey,
        @Schema(description = "문항 버전 번호", example = "1") int versionNo,
        @Schema(description = "문항 표시 순서", nullable = true, example = "1")
        Integer displayOrder,
        @Schema(description = "생성 방식", example = "HUMAN")
        QuizGenerationType generationType,
        @Schema(description = "버전 상태", example = "DRAFT") QuizQuestionStatus status
) {

    public static QuizQuestionCreateResponse from(QuizQuestion question) {
        return new QuizQuestionCreateResponse(
                question.getQuestionId(),
                question.getQuestionKey(),
                question.getVersionNo(),
                question.getDisplayOrder(),
                question.getGenerationType(),
                question.getStatus()
        );
    }
}
