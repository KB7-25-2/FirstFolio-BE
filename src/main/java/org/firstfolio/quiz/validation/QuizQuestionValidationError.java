package org.firstfolio.quiz.validation;

import java.util.Objects;

public record QuizQuestionValidationError(
        QuizQuestionValidationErrorCode code,
        String path,
        String message
) {

    public QuizQuestionValidationError {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
