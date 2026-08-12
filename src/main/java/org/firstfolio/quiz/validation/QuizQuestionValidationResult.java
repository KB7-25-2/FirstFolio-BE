package org.firstfolio.quiz.validation;

import java.util.List;
import java.util.Objects;

public record QuizQuestionValidationResult(List<QuizQuestionValidationError> errors) {

    public QuizQuestionValidationResult {
        Objects.requireNonNull(errors, "errors must not be null");
        errors = List.copyOf(errors);
    }

    public static QuizQuestionValidationResult valid() {
        return new QuizQuestionValidationResult(List.of());
    }

    public static QuizQuestionValidationResult invalid(QuizQuestionValidationError error) {
        return new QuizQuestionValidationResult(List.of(error));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
