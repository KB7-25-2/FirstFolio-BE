package org.firstfolio.quiz.integration.ai.dto.response;

import java.util.List;

public record QuizQuestionBatchResponse(
        String batchId,
        int total,
        int accepted,
        int rejected,
        List<QuizQuestionBatchItemResponse> items
) {

    public static QuizQuestionBatchResponse of(String batchId, List<QuizQuestionBatchItemResponse> items) {
        int acceptedCount = (int) items.stream()
                .filter(item -> "ACCEPTED".equals(item.result()))
                .count();

        return new QuizQuestionBatchResponse(
                batchId,
                items.size(),
                acceptedCount,
                items.size() - acceptedCount,
                items
        );
    }
}
