package org.firstfolio.content.exception;

import java.util.Objects;

public class ContentStorageException extends RuntimeException {

    private final ContentStorageError error;

    public ContentStorageException(ContentStorageError error, String message) {
        this(error, message, null);
    }

    public ContentStorageException(
            ContentStorageError error,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.error = Objects.requireNonNull(error, "error must not be null");
    }

    public ContentStorageError getError() {
        return error;
    }
}
