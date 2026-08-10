package org.firstfolio.learning.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record LessonContent(
        long subChapterId,
        String title,
        long contentVersionId,
        String schemaVersion,
        JsonNode lesson
) {
    public LessonContent {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(lesson, "lesson must not be null");
        lesson = lesson.deepCopy();
    }

    @Override
    public JsonNode lesson() {
        return lesson.deepCopy();
    }
}
