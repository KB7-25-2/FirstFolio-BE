package org.firstfolio.learning.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import org.firstfolio.learning.domain.LessonContent;

public record LessonContentResponse(
        long subChapterId,
        String title,
        long contentVersionId,
        String schemaVersion,
        JsonNode lesson
) {
    public static LessonContentResponse from(LessonContent content) {
        return new LessonContentResponse(
                content.subChapterId(),
                content.title(),
                content.contentVersionId(),
                content.schemaVersion(),
                content.lesson()
        );
    }
}
