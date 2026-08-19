package org.firstfolio.quiz.integration.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.integration.ai.service.QuizItemErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizQuestionBatchItemResponse(
        String itemId,
        String result,
        Long questionId,
        QuizQuestionStatus status,
        QuizItemErrorCode errorCode,
        String errorMessage
) {

    public static QuizQuestionBatchItemResponse accepted(
            String itemId, long questionId, QuizQuestionStatus status
    ) {
        return new QuizQuestionBatchItemResponse(itemId, "ACCEPTED", questionId, status, null, null);
    }

    public static QuizQuestionBatchItemResponse rejected(
            String itemId, QuizItemErrorCode errorCode, String errorMessage
    ) {
        return new QuizQuestionBatchItemResponse(itemId, "REJECTED", null, null, errorCode, errorMessage);
    }
}
