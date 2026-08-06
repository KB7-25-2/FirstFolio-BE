package org.firstfolio.content.storage;

import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

final class ContentObjectKeyValidator {

    private static final String RESERVED_VERSIONS_SEGMENT = ".versions";

    private ContentObjectKeyValidator() {
    }

    static String validate(String objectKey) {
        if (objectKey == null
                || objectKey.isBlank()
                || objectKey.startsWith("/")
                || objectKey.indexOf('\\') >= 0
                || objectKey.indexOf('\r') >= 0
                || objectKey.indexOf('\n') >= 0) {
            throw invalidObjectKey();
        }

        String[] segments = objectKey.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank()
                    || ".".equals(segment)
                    || "..".equals(segment)
                    || RESERVED_VERSIONS_SEGMENT.equals(segment)) {
                throw invalidObjectKey();
            }
        }

        try {
            Path normalized = Path.of(objectKey).normalize();
            if (normalized.isAbsolute()
                    || normalized.getNameCount() != segments.length
                    || !normalized.toString().replace('\\', '/').equals(objectKey)) {
                throw invalidObjectKey();
            }
        } catch (InvalidPathException exception) {
            throw invalidObjectKey();
        }

        return objectKey;
    }

    private static ContentStorageException invalidObjectKey() {
        return new ContentStorageException(
                ContentStorageError.INVALID_OBJECT_KEY,
                "Invalid content object key"
        );
    }
}
