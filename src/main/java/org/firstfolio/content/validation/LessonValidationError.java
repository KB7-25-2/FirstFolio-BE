package org.firstfolio.content.validation;

import java.util.Objects;

public record LessonValidationError(
        LessonValidationErrorCode code,
        String path,
        String message
) {

    public LessonValidationError {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
