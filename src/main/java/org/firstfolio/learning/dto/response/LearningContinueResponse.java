package org.firstfolio.learning.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.learning.domain.LearningContinueResult;
import org.firstfolio.learning.domain.LearningContinueTarget;

@Schema(description = "마지막 미완료 학습 위치와 이동 정보")
public record LearningContinueResponse(
        @Schema(description = "이어갈 학습 유형", example = "MAIN_CHAPTER_QUIZ")
        LearningContinueTarget targetType,
        @Schema(description = "개인 커리큘럼 항목 ID", example = "502") long curriculumItemId,
        @Schema(description = "대단원 ID", example = "2") long mainChapterId,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "소단원 ID. 강좌 또는 소단원 퀴즈 이어하기일 때 제공", example = "101", nullable = true)
        Long subChapterId,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "학습 중인 콘텐츠 버전 ID. 강좌 이어하기일 때만 제공", example = "301", nullable = true)
        Long contentVersionId,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "진행 중인 소단원 퀴즈 또는 진행 중·실패한 대단원 퀴즈 응시 ID", example = "3001", nullable = true)
        Long attemptId,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "JSON 내부 마지막 학습 페이지 ID", example = "page-2", nullable = true)
        String lastPageId,
        @Schema(description = "이어갈 위치의 진행률. 퀴즈 대상이면 선행 강좌 완료를 나타내는 100", example = "100")
        int progressPercent,
        @Schema(description = "프론트엔드 이동 경로", example = "/learning/sub-chapters/101?page=page-2")
        String route
) {
    public static LearningContinueResponse from(LearningContinueResult result) {
        return new LearningContinueResponse(
                result.targetType(),
                result.curriculumItemId(),
                result.mainChapterId(),
                result.subChapterId(),
                result.contentVersionId(),
                result.attemptId(),
                result.lastPageId(),
                result.progressPercent(),
                result.route()
        );
    }
}
