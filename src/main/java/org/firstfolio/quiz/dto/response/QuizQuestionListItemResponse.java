package org.firstfolio.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;

@Schema(description = "관리자용 퀴즈 문항 목록 항목")
public record QuizQuestionListItemResponse(
        @Schema(description = "문항 버전 ID", example = "1001") long questionId,
        @Schema(description = "논리 문항 키", example = "deposit-q-001") String questionKey,
        @Schema(description = "문항 버전 번호", example = "1") int versionNo,
        @Schema(description = "문항 사용처", example = "SUB_CHAPTER") QuizUsageType usageType,
        @Schema(description = "문항 유형", example = "SINGLE_CHOICE") QuizQuestionType questionType,
        @Schema(description = "생성 방식", example = "HUMAN") QuizGenerationType generationType,
        @Schema(description = "문항 지문", example = "예금의 특징으로 적절한 것은?") String prompt,
        @Schema(description = "버전 상태", example = "PUBLISHED") QuizQuestionStatus status
) {

    public static QuizQuestionListItemResponse from(QuizQuestion question) {
        return new QuizQuestionListItemResponse(
                question.getQuestionId(),
                question.getQuestionKey(),
                question.getVersionNo(),
                question.getUsageType(),
                question.getQuestionType(),
                question.getGenerationType(),
                question.getPrompt(),
                question.getStatus()
        );
    }
}
