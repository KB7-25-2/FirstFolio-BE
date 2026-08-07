package org.firstfolio.content.dto.request;

import com.fasterxml.jackson.databind.JsonNode;

public record LessonContentUploadRequest(
        Integer versionNo,
        JsonNode lesson
) {
}
