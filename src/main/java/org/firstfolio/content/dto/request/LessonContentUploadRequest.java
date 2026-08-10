package org.firstfolio.content.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소단원 학습 콘텐츠 새 버전")
public record LessonContentUploadRequest(
        @Schema(description = "소단원 안에서 증가하는 버전 번호", example = "2") Integer versionNo,
        @Schema(description = "검증할 학습 콘텐츠 JSON") JsonNode lesson
) {
}
