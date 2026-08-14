package org.firstfolio.quiz.integration.ai.dto.request;

public record QuizQuestionBatchItemRequest(
        String itemId,
        QuizQuestionRequest quiz
) {
}
