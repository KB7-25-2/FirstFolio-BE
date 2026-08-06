package org.firstfolio.content.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ContentStorageExceptionTest {

    @Test
    void keepsCommonErrorAndOriginalCause() {
        RuntimeException cause = new RuntimeException("filesystem failure");
        ContentStorageException exception = new ContentStorageException(
                ContentStorageError.STORAGE_UNAVAILABLE,
                "콘텐츠 저장소를 사용할 수 없습니다.",
                cause
        );

        assertEquals(ContentStorageError.STORAGE_UNAVAILABLE, exception.getError());
        assertSame(cause, exception.getCause());
    }
}
