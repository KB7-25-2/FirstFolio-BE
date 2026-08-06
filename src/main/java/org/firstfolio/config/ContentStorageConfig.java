package org.firstfolio.config;

import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.content.storage.LocalContentStorage;
import org.firstfolio.content.storage.S3ContentStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

@Configuration
public class ContentStorageConfig {

    private static final String DEFAULT_STORAGE_TYPE = "local";
    private static final String DEFAULT_LOCAL_ROOT = "./.local/content";
    private static final String DEFAULT_S3_PREFIX = "firstfolio";
    private static final long DEFAULT_MAX_BYTES = 5L * 1024L * 1024L;

    @Bean(destroyMethod = "close")
    public StaticContentStorage staticContentStorage(Environment environment) {
        String storageType = environment.getProperty(
                "content.storage.type",
                DEFAULT_STORAGE_TYPE
        );

        storageType = requireSingleLineText(
                storageType,
                "Content storage type must not be blank",
                "Content storage type must be a single line"
        );

        return switch (storageType.toLowerCase(Locale.ROOT)) {
            case "local" -> createLocalStorage(environment);
            case "s3" -> createS3Storage(environment);
            default -> throw configurationError(
                    "Unsupported content storage type. Expected local or s3",
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
        if (containsLineBreak(rootValue)) {
            throw configurationError(
                    "Local content storage root must be a single line",
                    null
            );
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

    private StaticContentStorage createS3Storage(Environment environment) {
        String bucket = requireSingleLineText(
                environment.getProperty("content.storage.s3.bucket"),
                "S3 content storage bucket must not be blank",
                "S3 content storage bucket must be a single line"
        );
        String regionName = requireSingleLineText(
                environment.getProperty("content.storage.s3.region"),
                "S3 content storage region must not be blank",
                "S3 content storage region must be a single line"
        );
        String prefix = environment.getProperty(
                "content.storage.s3.prefix",
                DEFAULT_S3_PREFIX
        );
        if (prefix == null) {
            prefix = DEFAULT_S3_PREFIX;
        }
        if (containsLineBreak(prefix)) {
            throw configurationError(
                    "S3 content storage prefix must be a single line",
                    null
            );
        }

        long maxBytes = parseMaxBytes(environment.getProperty(
                "content.storage.max-bytes",
                Long.toString(DEFAULT_MAX_BYTES)
        ));

        try {
            S3Client s3Client = S3Client.builder()
                    .region(Region.of(regionName))
                    .build();
            try {
                return new S3ContentStorage(s3Client, bucket, prefix, maxBytes);
            } catch (RuntimeException exception) {
                try {
                    s3Client.close();
                } catch (RuntimeException closeException) {
                    exception.addSuppressed(closeException);
                }
                throw exception;
            }
        } catch (ContentStorageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw configurationError("Failed to initialize S3 content storage", exception);
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

    private String requireSingleLineText(
            String value,
            String blankMessage,
            String multilineMessage
    ) {
        if (value == null || value.isBlank()) {
            throw configurationError(blankMessage, null);
        }
        if (containsLineBreak(value)) {
            throw configurationError(multilineMessage, null);
        }
        return value.trim();
    }

    private boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private ContentStorageException configurationError(String message, Throwable cause) {
        return new ContentStorageException(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                message,
                cause
        );
    }
}
