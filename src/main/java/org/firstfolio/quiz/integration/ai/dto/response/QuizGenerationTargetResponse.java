package org.firstfolio.quiz.integration.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.ChapterType;

import java.util.List;

@Schema(description = "AI 퀴즈 생성 대상 대단원·소단원 목록")
public record QuizGenerationTargetResponse(
        @Schema(description = "서비스 대상 대단원 목록")
        List<MainChapterTarget> mainChapters
) {

    @Schema(description = "AI 퀴즈 생성 대상 대단원")
    public record MainChapterTarget(
            @Schema(description = "대단원 ID", example = "2") long mainChapterId,
            @Schema(description = "대단원 이름. AI 생성 주제로 사용", example = "예·적금") String title,
            @Schema(description = "대단원 유형", example = "ASSET") ChapterType chapterType,
            @Schema(description = "해당 대단원의 서비스 대상 소단원 목록")
            List<SubChapterTarget> subChapters
    ) {
    }

    @Schema(description = "AI 퀴즈 생성 대상 소단원")
    public record SubChapterTarget(
            @Schema(description = "소단원 ID", example = "17") long subChapterId,
            @Schema(description = "소단원이 속한 대단원 ID", example = "2") long mainChapterId,
            @Schema(description = "소단원 이름. AI 생성 주제로 사용", example = "예금과 적금의 차이") String title
    ) {
    }
}
