package org.firstfolio.content.storage;

import org.firstfolio.content.domain.ContentWriteRequest;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.service.StaticContentStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 로컬 개발 환경에서 사용하는 불변 버전형 파일 저장소다.
 */
public final class LocalContentStorage implements StaticContentStorage {

    private static final String VERSIONS_DIRECTORY = ".versions";
    private static final String CONTENT_FILE = "content";
    private static final String CONTENT_TYPE_FILE = "content-type";
    private static final String LOCAL_VERSION_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}";

    private final Path rootDirectory;
    private final long maxBytes;
    private final Supplier<String> versionIdSupplier;

    public LocalContentStorage(Path rootDirectory, long maxBytes) {
        this(rootDirectory, maxBytes, () -> UUID.randomUUID().toString());
    }

    LocalContentStorage(
            Path rootDirectory,
            long maxBytes,
            Supplier<String> versionIdSupplier
    ) {
        this.rootDirectory = Objects.requireNonNull(
                rootDirectory,
                "rootDirectory must not be null"
        ).toAbsolutePath().normalize();
        this.versionIdSupplier = Objects.requireNonNull(
                versionIdSupplier,
                "versionIdSupplier must not be null"
        );

        if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE) {
            throw configurationError("maxBytes must be between 1 and Integer.MAX_VALUE", null);
        }
        this.maxBytes = maxBytes;

        initializeRootDirectory();
    }

    @Override
    public StoredObjectRef store(ContentWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        byte[] content = request.content();
        validateContentSize(content.length);

        Path objectDirectory = resolveObjectDirectory(request.objectKey());
        Path versionsDirectory = objectDirectory.resolve(VERSIONS_DIRECTORY);
        String versionId = nextVersionId();
        Path versionDirectory = versionsDirectory.resolve(versionId);
        Path temporaryDirectory = null;

        try {
            ensureNoSymbolicLinks(objectDirectory);
            Files.createDirectories(versionsDirectory);
            ensureNoSymbolicLinks(versionsDirectory);

            temporaryDirectory = Files.createTempDirectory(versionsDirectory, ".tmp-");
            Files.write(
                    temporaryDirectory.resolve(CONTENT_FILE),
                    content,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            Files.writeString(
                    temporaryDirectory.resolve(CONTENT_TYPE_FILE),
                    request.contentType(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            moveCompletedVersion(temporaryDirectory, versionDirectory);
            temporaryDirectory = null;
            return new StoredObjectRef(request.objectKey(), versionId);
        } catch (FileAlreadyExistsException exception) {
            throw storageUnavailable("A local content version already exists", exception);
        } catch (IOException | SecurityException exception) {
            throw storageUnavailable("Failed to store local content", exception);
        } finally {
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    @Override
    public StoredContent load(StoredObjectRef reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        Path objectDirectory = resolveObjectDirectory(reference.objectKey());
        String versionId = validateLocalVersionId(
                reference.versionId(),
                ContentStorageError.INVALID_OBJECT_KEY
        );
        Path versionDirectory = objectDirectory
                .resolve(VERSIONS_DIRECTORY)
                .resolve(versionId);
        Path contentPath = versionDirectory.resolve(CONTENT_FILE);
        Path contentTypePath = versionDirectory.resolve(CONTENT_TYPE_FILE);

        try {
            ensureNoSymbolicLinks(contentPath);
            ensureNoSymbolicLinks(contentTypePath);

            if (!Files.isRegularFile(contentPath, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(contentTypePath, LinkOption.NOFOLLOW_LINKS)) {
                throw objectNotFound(reference, null);
            }

            long contentSize = Files.size(contentPath);
            validateContentSize(contentSize);

            byte[] content = Files.readAllBytes(contentPath);
            String contentType = Files.readString(
                    contentTypePath,
                    StandardCharsets.UTF_8
            );
            try {
                return new StoredContent(content, contentType);
            } catch (IllegalArgumentException exception) {
                throw storageUnavailable("Local content metadata is invalid", exception);
            }
        } catch (NoSuchFileException exception) {
            throw objectNotFound(reference, exception);
        } catch (IOException | SecurityException exception) {
            throw storageUnavailable("Failed to load local content", exception);
        }
    }

    private void initializeRootDirectory() {
        try {
            if (Files.isSymbolicLink(rootDirectory)) {
                throw configurationError("Local storage root must not be a symbolic link", null);
            }
            Files.createDirectories(rootDirectory);
            if (!Files.isDirectory(rootDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw configurationError("Local storage root is not a directory", null);
            }
        } catch (IOException | SecurityException exception) {
            throw configurationError("Failed to initialize local storage root", exception);
        }
    }

    private Path resolveObjectDirectory(String objectKey) {
        ContentObjectKeyValidator.validate(objectKey);

        Path resolved;
        try {
            resolved = rootDirectory.resolve(objectKey).normalize();
        } catch (InvalidPathException exception) {
            throw invalidObjectKey();
        }
        if (!resolved.startsWith(rootDirectory) || resolved.equals(rootDirectory)) {
            throw invalidObjectKey();
        }
        return resolved;
    }

    private String nextVersionId() {
        String versionId = versionIdSupplier.get();
        return validateLocalVersionId(
                versionId,
                ContentStorageError.STORAGE_CONFIGURATION_ERROR
        );
    }

    private String validateLocalVersionId(
            String versionId,
            ContentStorageError error
    ) {
        if (versionId == null || !versionId.matches(LOCAL_VERSION_PATTERN)) {
            throw new ContentStorageException(
                    error,
                    "Invalid local content version ID"
            );
        }
        return versionId;
    }

    private void validateContentSize(long contentSize) {
        if (contentSize > maxBytes) {
            throw new ContentStorageException(
                    ContentStorageError.CONTENT_TOO_LARGE,
                    "Content exceeds the configured local storage limit"
            );
        }
    }

    private void ensureNoSymbolicLinks(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(rootDirectory)) {
            throw invalidObjectKey();
        }

        Path current = rootDirectory;
        if (Files.isSymbolicLink(current)) {
            throw invalidObjectKey();
        }
        for (Path segment : rootDirectory.relativize(normalized)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw invalidObjectKey();
            }
        }
    }

    private void moveCompletedVersion(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void deleteTemporaryDirectory(Path temporaryDirectory) {
        if (temporaryDirectory == null || !temporaryDirectory.startsWith(rootDirectory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(temporaryDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException ignored) {
            // 원래 저장 실패를 가리지 않도록 임시 경로 정리 오류는 무시한다.
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 다음 로컬 정리 시 제거할 수 있도록 남겨 둔다.
        }
    }

    private ContentStorageException invalidObjectKey() {
        return new ContentStorageException(
                ContentStorageError.INVALID_OBJECT_KEY,
                "Invalid local content object key"
        );
    }

    private ContentStorageException objectNotFound(
            StoredObjectRef reference,
            Throwable cause
    ) {
        return new ContentStorageException(
                ContentStorageError.OBJECT_NOT_FOUND,
                "Local content object version was not found: "
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

    private ContentStorageException configurationError(String message, Throwable cause) {
        return new ContentStorageException(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                message,
                cause
        );
    }
}
