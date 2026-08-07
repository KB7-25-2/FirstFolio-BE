package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.MainChapter;

import java.util.List;

@Schema(description = "대단원 목록")
public record MainChapterListResponse(
        @Schema(description = "노출 순서대로 정렬된 대단원") List<MainChapterListItemResponse> items
) {
    public static MainChapterListResponse from(List<MainChapter> chapters) {
        return new MainChapterListResponse(
                chapters.stream().map(MainChapterListItemResponse::from).toList()
        );
    }
}
