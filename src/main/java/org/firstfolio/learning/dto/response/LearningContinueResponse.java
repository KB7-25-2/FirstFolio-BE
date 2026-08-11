package org.firstfolio.learning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.learning.domain.LearningContinueResult;

@Schema(description = "마지막 미완료 학습 위치와 이동 정보")
public record LearningContinueResponse(
        @Schema(description = "개인 커리큘럼 항목 ID", example = "502") long curriculumItemId,
        @Schema(description = "대단원 ID", example = "2") long mainChapterId,
        @Schema(description = "소단원 ID", example = "101") long subChapterId,
        @Schema(description = "학습 중인 콘텐츠 버전 ID", example = "301") long contentVersionId,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "JSON 내부 마지막 학습 페이지 ID", example = "page-2", nullable = true)
        String lastPageId,
        @Schema(description = "현재 소단원 페이지 진행률", example = "35") int progressPercent,
        @Schema(description = "프론트엔드 이동 경로", example = "/learning/sub-chapters/101?page=page-2")
        String route
) {
    public static LearningContinueResponse from(LearningContinueResult result) {
        return new LearningContinueResponse(
                result.curriculumItemId(),
                result.mainChapterId(),
                result.subChapterId(),
                result.contentVersionId(),
                result.lastPageId(),
                result.progressPercent(),
                result.route()
        );
    }
}
