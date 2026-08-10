package org.firstfolio.learning.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.learning.domain.LessonContent;

@Schema(description = "현재 공개된 소단원 학습 콘텐츠")
public record LessonContentResponse(
        @Schema(description = "소단원 ID", example = "21") long subChapterId,
        @Schema(description = "소단원 제목", example = "예금과 적금의 차이") String title,
        @Schema(description = "현재 공개 버전 ID", example = "301") long contentVersionId,
        @Schema(description = "lesson JSON 스키마 버전", example = "1.0") String schemaVersion,
        @Schema(description = "버전형 학습 콘텐츠 JSON") JsonNode lesson
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
