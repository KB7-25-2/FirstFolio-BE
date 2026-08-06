package org.firstfolio.content.storage;

import org.firstfolio.content.domain.ContentWriteRequest;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalContentStorageTest {

    private static final String OBJECT_KEY = "learning/sub-chapters/101/content.json";

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndLoadsExactContentVersion() {
        LocalContentStorage storage = storage(1024);
        byte[] content = "{\"pages\":[]}".getBytes();

        StoredObjectRef reference = storage.store(
                new ContentWriteRequest(OBJECT_KEY, content, "application/json")
        );
        StoredContent loaded = storage.load(reference);

        assertEquals(OBJECT_KEY, reference.objectKey());
        assertArrayEquals(content, loaded.content());
        assertEquals("application/json", loaded.contentType());
    }

    @Test
    void keepsEarlierVersionWhenSameObjectKeyIsStoredAgain() {
        AtomicInteger sequence = new AtomicInteger();
        LocalContentStorage storage = new LocalContentStorage(
                temporaryDirectory.resolve("content"),
                1024,
                () -> "version-" + sequence.incrementAndGet()
        );

        StoredObjectRef first = storage.store(request("first"));
        StoredObjectRef second = storage.store(request("second"));

        assertNotEquals(first.versionId(), second.versionId());
        assertArrayEquals("first".getBytes(), storage.load(first).content());
        assertArrayEquals("second".getBytes(), storage.load(second).content());
    }

    @Test
    void rejectsContentLargerThanConfiguredLimit() {
        LocalContentStorage storage = storage(3);

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.store(request("four"))
        );

        assertEquals(ContentStorageError.CONTENT_TOO_LARGE, exception.getError());
    }

    @Test
    void rejectsObjectKeysThatCanEscapeOrChangePathMeaning() {
        LocalContentStorage storage = storage(1024);

        assertInvalidObjectKey(storage, "../outside.json");
        assertInvalidObjectKey(storage, "/absolute.json");
        assertInvalidObjectKey(storage, "learning\\content.json");
        assertInvalidObjectKey(storage, "learning//content.json");
        assertInvalidObjectKey(storage, "learning/./content.json");
        assertInvalidObjectKey(storage, "learning/.versions/content.json");
        assertInvalidObjectKey(storage, "learning/" + (char) 0 + "/content.json");
    }

    @Test
    void rejectsVersionIdThatIsNotASafeLocalPathSegment() {
        LocalContentStorage storage = storage(1024);

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.load(new StoredObjectRef(OBJECT_KEY, "../outside"))
        );

        assertEquals(ContentStorageError.INVALID_OBJECT_KEY, exception.getError());
    }

    @Test
    void rejectsSymbolicLinkInsideStorageRoot() throws IOException {
        Path root = temporaryDirectory.resolve("content");
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(root);
        Files.createDirectories(outside);
        Files.createSymbolicLink(root.resolve("escape"), outside);
        LocalContentStorage storage = new LocalContentStorage(root, 1024);

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.store(request("escape/content.json", "content"))
        );

        assertEquals(ContentStorageError.INVALID_OBJECT_KEY, exception.getError());
    }

    @Test
    void returnsNotFoundForUnknownObjectVersion() {
        LocalContentStorage storage = storage(1024);

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.load(new StoredObjectRef(OBJECT_KEY, "missing-version"))
        );

        assertEquals(ContentStorageError.OBJECT_NOT_FOUND, exception.getError());
    }

    @Test
    void rejectsInvalidMaximumSizeConfiguration() {
        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> new LocalContentStorage(temporaryDirectory.resolve("content"), 0)
        );

        assertEquals(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                exception.getError()
        );
    }

    @Test
    void treatsInvalidGeneratedVersionIdAsConfigurationError() {
        LocalContentStorage storage = new LocalContentStorage(
                temporaryDirectory.resolve("content"),
                1024,
                () -> "../invalid"
        );

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.store(request("content"))
        );

        assertEquals(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                exception.getError()
        );
    }

    private LocalContentStorage storage(long maxBytes) {
        return new LocalContentStorage(temporaryDirectory.resolve("content"), maxBytes);
    }

    private ContentWriteRequest request(String content) {
        return request(OBJECT_KEY, content);
    }

    private ContentWriteRequest request(String objectKey, String content) {
        return new ContentWriteRequest(
                objectKey,
                content.getBytes(),
                "application/json"
        );
    }

    private void assertInvalidObjectKey(LocalContentStorage storage, String objectKey) {
        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> storage.store(request(objectKey, "content"))
        );
        assertEquals(ContentStorageError.INVALID_OBJECT_KEY, exception.getError());
    }
}
