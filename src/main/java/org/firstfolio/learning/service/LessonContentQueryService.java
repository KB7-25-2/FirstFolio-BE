package org.firstfolio.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.content.domain.PublishedLessonReference;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.exception.ContentStorageException;
import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.curriculum.domain.SubChapter;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.learning.domain.LessonContent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class LessonContentQueryService {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final ContentVersionMapper contentVersionMapper;
    private final SubChapterMapper subChapterMapper;
    private final StaticContentStorage contentStorage;
    private final ObjectMapper objectMapper;

    public LessonContentQueryService(
            ContentVersionMapper contentVersionMapper,
            SubChapterMapper subChapterMapper,
            StaticContentStorage contentStorage
    ) {
        this(
                contentVersionMapper,
                subChapterMapper,
                contentStorage,
                new ObjectMapper()
        );
    }

    LessonContentQueryService(
            ContentVersionMapper contentVersionMapper,
            SubChapterMapper subChapterMapper,
            StaticContentStorage contentStorage,
            ObjectMapper objectMapper
    ) {
        this.contentVersionMapper = contentVersionMapper;
        this.subChapterMapper = subChapterMapper;
        this.contentStorage = contentStorage;
        this.objectMapper = objectMapper;
    }

    public LessonContent getPublishedLesson(long subChapterId) {
        PublishedLessonReference reference =
                contentVersionMapper.findCurrentPublishedLesson(subChapterId);
        if (reference == null) {
            throw missingPublishedContent(subChapterId);
        }

        StoredContent storedContent;
        try {
            storedContent = contentStorage.load(reference.toStoredObjectRef());
        } catch (ContentStorageException exception) {
            throw unavailable(exception);
        }

        JsonNode lesson = parseLesson(storedContent, reference.getSchemaVersion());
        return new LessonContent(
                reference.getSubChapterId(),
                reference.getTitle(),
                reference.getContentVersionId(),
                reference.getSchemaVersion(),
                lesson
        );
    }

    private ApiException missingPublishedContent(long subChapterId) {
        SubChapter subChapter = subChapterMapper.findById(subChapterId);
        if (subChapter == null || !subChapter.isActive()) {
            return new ApiException(ErrorCode.SUB_CHAPTER_NOT_FOUND);
        }
        return new ApiException(ErrorCode.CONTENT_NOT_PUBLISHED);
    }

    private JsonNode parseLesson(
            StoredContent storedContent,
            String expectedSchemaVersion
    ) {
        if (!isJson(storedContent.contentType())) {
            throw unavailable(null);
        }

        try {
            JsonNode lesson = objectMapper.readTree(new String(
                    storedContent.content(),
                    StandardCharsets.UTF_8
            ));
            if (lesson == null
                    || !lesson.isObject()
                    || !expectedSchemaVersion.equals(
                            lesson.path("schemaVersion").textValue()
                    )) {
                throw unavailable(null);
            }
            return lesson;
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private boolean isJson(String contentType) {
        return JSON_CONTENT_TYPE.equalsIgnoreCase(
                contentType.split(";", 2)[0].trim()
        );
    }

    private ApiException unavailable(Throwable cause) {
        return new ApiException(
                ErrorCode.CONTENT_UNAVAILABLE,
                ErrorCode.CONTENT_UNAVAILABLE.getDefaultMessage(),
                cause
        );
    }
}
