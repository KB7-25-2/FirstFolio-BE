package org.firstfolio.config;

import org.firstfolio.content.domain.ContentWriteRequest;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.exception.ContentStorageError;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.content.storage.LocalContentStorage;
import org.firstfolio.content.storage.S3ContentStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContentStorageConfigTest {

    @TempDir
    Path temporaryDirectory;

    private final ContentStorageConfig config = new ContentStorageConfig();

    @Test
    void registersLocalStorageBeanByDefault() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "contentStorageConfigTest",
                            Map.of(
                                    "content.storage.local-root",
                                    temporaryDirectory.resolve("content").toString(),
                                    "content.storage.max-bytes",
                                    "1024"
                            )
                    )
            );
            context.register(ContentStorageConfig.class);
            context.refresh();

            StaticContentStorage storage = context.getBean(StaticContentStorage.class);
            StoredObjectRef reference = storage.store(
                    new ContentWriteRequest(
                            "learning/content.json",
                            "{}".getBytes(),
                            "application/json"
                    )
            );

            assertInstanceOf(LocalContentStorage.class, storage);
            assertArrayEquals("{}".getBytes(), storage.load(reference).content());
        }
    }

    @Test
    void rejectsUnsupportedStorageType() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("content.storage.type", "unknown");

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> config.staticContentStorage(environment)
        );

        assertEquals(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                exception.getError()
        );
    }

    @Test
    void registersS3StorageWhenRequiredSettingsExist() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("content.storage.type", "s3")
                .withProperty("content.storage.s3.bucket", "firstfolio-content")
                .withProperty("content.storage.s3.prefix", "firstfolio")
                .withProperty("content.storage.s3.region", "ap-northeast-2")
                .withProperty("content.storage.max-bytes", "1024");

        try (StaticContentStorage storage = config.staticContentStorage(environment)) {
            assertInstanceOf(S3ContentStorage.class, storage);
        }
    }

    @Test
    void rejectsS3WithoutBucket() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("content.storage.type", "s3")
                .withProperty("content.storage.s3.region", "ap-northeast-2");

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> config.staticContentStorage(environment)
        );

        assertEquals(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                exception.getError()
        );
    }

    @Test
    void rejectsMultilineStorageTypeWithoutIncludingItsValueInTheError() {
        String pastedSettings = "s3\nCONTENT_S3_BUCKET=do-not-log-this";
        MockEnvironment environment = new MockEnvironment()
                .withProperty("content.storage.type", pastedSettings);

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> config.staticContentStorage(environment)
        );

        assertEquals(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                exception.getError()
        );
        assertFalse(exception.getMessage().contains("do-not-log-this"));
    }

    @Test
    void rejectsNonNumericMaximumSize() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("content.storage.type", "local")
                .withProperty(
                        "content.storage.local-root",
                        temporaryDirectory.resolve("content").toString()
                )
                .withProperty("content.storage.max-bytes", "not-a-number");

        ContentStorageException exception = assertThrows(
                ContentStorageException.class,
                () -> config.staticContentStorage(environment)
        );

        assertEquals(
                ContentStorageError.STORAGE_CONFIGURATION_ERROR,
                exception.getError()
        );
    }
}
