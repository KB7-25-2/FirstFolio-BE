package org.firstfolio.learning.service;

import org.firstfolio.content.domain.ContentWriteRequest;
import org.firstfolio.content.domain.PublishedLessonReference;
import org.firstfolio.content.domain.StoredObjectRef;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.storage.LocalContentStorage;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.learning.domain.LessonContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LessonContentLocalStorageIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsPublishedLessonFromLocalImmutableVersion() {
        String objectKey = "learning/sub-chapters/103/lesson.json";
        String json = """
                {
                  "schemaVersion": "1.0",
                  "pages": [{"id": "page-1", "blocks": []}],
                  "subChapterQuiz": {"questionIds": [1021]}
                }
                """;
        LocalContentStorage storage = new LocalContentStorage(
                temporaryDirectory.resolve("content"),
                5L * 1024L * 1024L
        );
        StoredObjectRef storedObject = storage.store(new ContentWriteRequest(
                objectKey,
                json.getBytes(StandardCharsets.UTF_8),
                "application/json"
        ));

        ContentVersionMapper contentVersionMapper = mock(ContentVersionMapper.class);
        PublishedLessonReference reference = new PublishedLessonReference();
        reference.setSubChapterId(103L);
        reference.setTitle("예금의 기초");
        reference.setContentVersionId(302L);
        reference.setSchemaVersion("1.0");
        reference.setStorageObjectKey(storedObject.objectKey());
        reference.setStorageVersionId(storedObject.versionId());
        when(contentVersionMapper.findCurrentPublishedLesson(103L)).thenReturn(reference);

        LessonContentQueryService service = new LessonContentQueryService(
                contentVersionMapper,
                mock(SubChapterMapper.class),
                storage
        );

        LessonContent content = service.getPublishedLesson(103L);

        assertEquals(302L, content.contentVersionId());
        assertEquals("page-1", content.lesson().path("pages").get(0).path("id").textValue());
    }
}
