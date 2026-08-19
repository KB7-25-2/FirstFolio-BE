package org.firstfolio.quiz.integration.ai.service;

public record QuizItemValidationResult(
        QuizItemErrorCode errorCode,
        String errorMessage
) {

    public static QuizItemValidationResult valid() {
        return new QuizItemValidationResult(null, null);
    }

    public static QuizItemValidationResult invalid(QuizItemErrorCode errorCode, String errorMessage) {
        return new QuizItemValidationResult(errorCode, errorMessage);
    }

    public boolean isValid() {
        return errorCode == null;
    }
}
