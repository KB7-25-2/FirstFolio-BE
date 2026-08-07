package org.firstfolio.content.storage;

import org.firstfolio.content.domain.ContentWriteRequest;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.service.StaticContentStorage;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 운영 환경에서 사용하는 S3 Versioning 기반 정적 콘텐츠 저장소다.
 */
public final class S3ContentStorage implements StaticContentStorage {

    private static final int READ_BUFFER_SIZE = 8192;
    private static final Set<String> NOT_FOUND_ERROR_CODES = Set.of(
            "nosuchkey",
            "nosuchversion"
    );

    private final S3Client s3Client;
    private final String bucket;
    private final String keyPrefix;
    private final long maxBytes;

    public S3ContentStorage(
            S3Client s3Client,
            String bucket,
            String keyPrefix,
            long maxBytes
    ) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
        this.bucket = requireSingleLineText(bucket, "bucket");
        this.keyPrefix = normalizePrefix(keyPrefix);

        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE) {
            throw configurationError("maxBytes must be between 1 and Integer.MAX_VALUE", null);
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public StoredObjectRef store(ContentWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String objectKey = ContentObjectKeyValidator.validate(request.objectKey());
        byte[] content = request.content();
        validateContentSize(content.length);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(toS3Key(objectKey))
                .contentType(request.contentType())
                .build();

        try {
            PutObjectResponse response = s3Client.putObject(
                    putRequest,
                    RequestBody.fromBytes(content)
            );
            String versionId = response.versionId();
            if (versionId == null
                    || versionId.isBlank()
                    || "null".equalsIgnoreCase(versionId)) {
                throw configurationError(
                        "S3 did not return a version ID; enable Versioning on the content bucket",
                        null
                );
            }
            return new StoredObjectRef(objectKey, versionId);
        } catch (ContentStorageException exception) {
            throw exception;
        } catch (S3Exception | SdkClientException exception) {
            throw storageUnavailable("Failed to store S3 content", exception);
        }
    }

    @Override
    public StoredContent load(StoredObjectRef reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        String objectKey = ContentObjectKeyValidator.validate(reference.objectKey());
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(toS3Key(objectKey))
                .versionId(reference.versionId())
                .build();

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest)) {
            GetObjectResponse metadata = response.response();
            Long contentLength = metadata.contentLength();
            if (contentLength != null) {
                validateContentSize(contentLength);
            }

            byte[] content = readWithinLimit(response);
            try {
                return new StoredContent(content, metadata.contentType());
            } catch (IllegalArgumentException exception) {
                throw storageUnavailable("S3 content metadata is invalid", exception);
            }
        } catch (ContentStorageException exception) {
            throw exception;
        } catch (S3Exception exception) {
            if (isNotFound(exception)) {
                throw objectNotFound(reference, exception);
            }
            throw storageUnavailable("Failed to load S3 content", exception);
        } catch (SdkClientException | IOException exception) {
            throw storageUnavailable("Failed to load S3 content", exception);
        }
    }

    @Override
    public void close() {
        s3Client.close();
    }

    private byte[] readWithinLimit(ResponseInputStream<GetObjectResponse> response)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        long totalBytes = 0;
        int read;
        while ((read = response.read(buffer)) != -1) {
            totalBytes += read;
            validateContentSize(totalBytes);
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String toS3Key(String objectKey) {
        return keyPrefix.isEmpty() ? objectKey : keyPrefix + "/" + objectKey;
    }

    private boolean isNotFound(S3Exception exception) {
        if (exception.statusCode() == 404) {
            return true;
        }
        if (exception.awsErrorDetails() == null
                || exception.awsErrorDetails().errorCode() == null) {
            return false;
        }
        return NOT_FOUND_ERROR_CODES.contains(
                exception.awsErrorDetails().errorCode().toLowerCase(Locale.ROOT)
        );
    }

    private void validateContentSize(long contentSize) {
        if (contentSize > maxBytes) {
            throw new ContentStorageException(
                    ContentStorageError.CONTENT_TOO_LARGE,
                    "Content exceeds the configured S3 storage limit"
            );
        }
    }

    private static String normalizePrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return "";
        }
        try {
            return ContentObjectKeyValidator.validate(keyPrefix.trim());
        } catch (ContentStorageException exception) {
            throw configurationError("Invalid S3 content storage prefix", exception);
        }
    }

    private static String requireSingleLineText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw configurationError(fieldName + " must not be blank", null);
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw configurationError(fieldName + " must be a single line", null);
        }
        return value.trim();
    }

    private ContentStorageException objectNotFound(
            StoredObjectRef reference,
            Throwable cause
    ) {
        return new ContentStorageException(
                ContentStorageError.OBJECT_NOT_FOUND,
                "S3 content object version was not found: "
                        + reference.objectKey() + "@" + reference.versionId(),
                cause
        );
    }

    private ContentStorageException storageUnavailable(String message, Throwable cause) {
        return new ContentStorageException(
                ContentStorageError.STORAGE_UNAVAILABLE,
                message,
                cause
        );
    }

    private static ContentStorageException configurationError(
            String message,
            Throwable cause
    ) {
        return new ContentStorageException(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                message,
                cause
        );
    }
}
