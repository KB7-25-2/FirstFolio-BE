package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.SubChapter;

import java.util.List;

@Schema(description = "대단원에 속한 소단원 목록")
public record SubChapterListResponse(
        @Schema(description = "노출 순서대로 정렬된 소단원") List<SubChapterListItemResponse> items
) {
    public static SubChapterListResponse from(List<SubChapter> chapters) {
        return new SubChapterListResponse(
                chapters.stream().map(SubChapterListItemResponse::from).toList()
        );
    }
}
