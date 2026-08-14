package org.firstfolio.quiz.integration.ai.dto.request;

public record QuizOptionRequest(
        String key,
        String label,
        String description
) {
}
