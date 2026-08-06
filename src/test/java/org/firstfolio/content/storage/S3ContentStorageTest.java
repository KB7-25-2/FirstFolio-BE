package org.firstfolio.content.storage;

import org.firstfolio.content.domain.ContentWriteRequest;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ContentStorageTest {

    private static final String BUCKET = "firstfolio-content";
    private static final String PREFIX = "firstfolio";
    private static final String OBJECT_KEY = "learning/sub-chapters/101/content.json";

    @Mock
    S3Client s3Client;

    @Test
    void storesContentAndReturnsS3VersionId() throws IOException {
        S3ContentStorage storage = storage(1024);
        byte[] content = "{\"pages\":[]}".getBytes(StandardCharsets.UTF_8);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().versionId("version-1").build());

        StoredObjectRef reference = storage.store(
                new ContentWriteRequest(OBJECT_KEY, content, "application/json")
        );

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest request = requestCaptor.getValue();
        assertEquals(BUCKET, request.bucket());
        assertEquals(PREFIX + "/" + OBJECT_KEY, request.key());
        assertEquals("application/json", request.contentType());
        assertArrayEquals(
                content,
                bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes()
        );
        assertEquals(OBJECT_KEY, reference.objectKey());
        assertEquals("version-1", reference.versionId());
    }

    @Test
    void rejectsUploadWhenBucketDoesNotReturnVersionId() {
        S3ContentStorage storage = storage(1024);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.store(request("content"))
        );

        assertEquals(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                exception.getError()
        );
    }

    @Test
    void loadsTheRequestedS3ObjectVersion() {
        S3ContentStorage storage = storage(1024);
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(response(content, "application/json", (long) content.length));

        StoredContent loaded = storage.load(
                new StoredObjectRef(OBJECT_KEY, "version-2")
        );

        ArgumentCaptor<GetObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture());
        GetObjectRequest request = requestCaptor.getValue();
        assertEquals(BUCKET, request.bucket());
        assertEquals(PREFIX + "/" + OBJECT_KEY, request.key());
        assertEquals("version-2", request.versionId());
        assertArrayEquals(content, loaded.content());
        assertEquals("application/json", loaded.contentType());
    }

    @Test
    void returnsNotFoundForMissingS3ObjectVersion() {
        S3ContentStorage storage = storage(1024);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(404).message("missing").build()
        );

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.load(new StoredObjectRef(OBJECT_KEY, "missing"))
        );

        assertEquals(ContentStorageError.OBJECT_NOT_FOUND, exception.getError());
    }

    @Test
    void mapsSdkConnectionFailureToStorageUnavailable() {
        S3ContentStorage storage = storage(1024);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(
                SdkClientException.create("unavailable")
        );

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.load(new StoredObjectRef(OBJECT_KEY, "version-1"))
        );

        assertEquals(ContentStorageError.STORAGE_UNAVAILABLE, exception.getError());
    }

    @Test
    void rejectsContentLargerThanConfiguredLimitOnWrite() {
        S3ContentStorage storage = storage(3);

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.store(request("four"))
        );

        assertEquals(ContentStorageError.CONTENT_TOO_LARGE, exception.getError());
    }

    @Test
    void rejectsResponseLargerThanConfiguredLimit() {
        S3ContentStorage storage = storage(3);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(response("four".getBytes(), "text/plain", null));

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.load(new StoredObjectRef(OBJECT_KEY, "version-1"))
        );

        assertEquals(ContentStorageError.CONTENT_TOO_LARGE, exception.getError());
    }

    @Test
    void rejectsInvalidObjectKeyBeforeCallingS3() {
        S3ContentStorage storage = storage(1024);

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.store(new ContentWriteRequest(
                        "../outside.json",
                        "content".getBytes(),
                        "application/json"
                ))
        );

        assertEquals(ContentStorageError.INVALID_OBJECT_KEY, exception.getError());
    }

    @Test
    void closesS3Client() {
        S3ContentStorage storage = storage(1024);

        storage.close();

        verify(s3Client).close();
    }

    @Test
    void treatsInvalidPrefixAsConfigurationError() {
        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> new S3ContentStorage(s3Client, BUCKET, "../outside", 1024)
        );

        assertEquals(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                exception.getError()
        );
    }

    private S3ContentStorage storage(long maxBytes) {
        return new S3ContentStorage(s3Client, BUCKET, PREFIX, maxBytes);
    }

    private ContentWriteRequest request(String content) {
        return new ContentWriteRequest(
                OBJECT_KEY,
                content.getBytes(StandardCharsets.UTF_8),
                "application/json"
        );
    }

    private ResponseInputStream<GetObjectResponse> response(
            byte[] content,
            String contentType,
            Long contentLength
    ) {
        GetObjectResponse response = GetObjectResponse.builder()
                .contentLength(contentLength)
                .contentType(contentType)
                .build();
        return new ResponseInputStream<>(
                response,
                AbortableInputStream.create(new ByteArrayInputStream(content))
        );
    }
}
