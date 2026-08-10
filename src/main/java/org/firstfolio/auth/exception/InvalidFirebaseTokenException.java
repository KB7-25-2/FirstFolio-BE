package org.firstfolio.auth.exception;

public class InvalidFirebaseTokenException extends RuntimeException {

    public InvalidFirebaseTokenException(Throwable cause) {
        super(cause);
    }

    public InvalidFirebaseTokenException() {
        super();
    }
}
