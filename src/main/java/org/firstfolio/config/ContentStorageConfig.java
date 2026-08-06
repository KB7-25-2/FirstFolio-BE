package org.firstfolio.config;

import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.content.storage.LocalContentStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

@Configuration
public class ContentStorageConfig {

    private static final String DEFAULT_STORAGE_TYPE = "local";
    private static final String DEFAULT_LOCAL_ROOT = "./.local/content";
    private static final long DEFAULT_MAX_BYTES = 5L * 1024L * 1024L;

    @Bean
    public StaticContentStorage staticContentStorage(Environment environment) {
        String storageType = environment.getProperty(
                "content.storage.type",
                DEFAULT_STORAGE_TYPE
        );

        if (storageType == null || storageType.isBlank()) {
            throw configurationError("Content storage type must not be blank", null);
        }

        return switch (storageType.trim().toLowerCase(Locale.ROOT)) {
            case "local" -> createLocalStorage(environment);
            case "s3" -> throw configurationError(
                    "S3 content storage is not implemented yet",
                    null
            );
            default -> throw configurationError(
                    "Unsupported content storage type: " + storageType,
                    null
            );
        };
    }

    private StaticContentStorage createLocalStorage(Environment environment) {
        String rootValue = environment.getProperty(
                "content.storage.local-root",
                DEFAULT_LOCAL_ROOT
        );
        if (rootValue == null || rootValue.isBlank()) {
            throw configurationError("Local content storage root must not be blank", null);
        }

        String maxBytesValue = environment.getProperty(
                "content.storage.max-bytes",
                Long.toString(DEFAULT_MAX_BYTES)
        );
        long maxBytes = parseMaxBytes(maxBytesValue);

        try {
            return new LocalContentStorage(Path.of(rootValue.trim()), maxBytes);
        } catch (InvalidPathException exception) {
            throw configurationError("Local content storage root is invalid", exception);
        }
    }

    private long parseMaxBytes(String value) {
        if (value == null || value.isBlank()) {
            throw configurationError("Content storage max bytes must not be blank", null);
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw configurationError("Content storage max bytes must be a number", exception);
        }
    }

    private ContentStorageException configurationError(String message, Throwable cause) {
        return new ContentStorageException(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                message,
                cause
        );
    }
}
