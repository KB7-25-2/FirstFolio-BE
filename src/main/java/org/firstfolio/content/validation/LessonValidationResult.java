package org.firstfolio.content.validation;

import java.util.List;
import java.util.Objects;

public record LessonValidationResult(List<LessonValidationError> errors) {

    public LessonValidationResult {
        Objects.requireNonNull(errors, "errors must not be null");
        errors = List.copyOf(errors);
    }

    public static LessonValidationResult valid() {
        return new LessonValidationResult(List.of());
    }

    public static LessonValidationResult invalid(LessonValidationError error) {
        return new LessonValidationResult(List.of(error));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
