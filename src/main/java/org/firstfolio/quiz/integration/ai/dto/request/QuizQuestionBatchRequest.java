package org.firstfolio.quiz.integration.ai.dto.request;

import java.util.List;

public record QuizQuestionBatchRequest(
        String batchId,
        List<QuizQuestionBatchItemRequest> items
) {
}
