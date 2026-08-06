package org.firstfolio.curriculum.dto.response;

import org.firstfolio.curriculum.domain.SubChapter;

import java.util.List;

public record SubChapterListResponse(
        List<SubChapterListItemResponse> items
) {
    public static SubChapterListResponse from(List<SubChapter> chapters) {
        return new SubChapterListResponse(
                chapters.stream().map(SubChapterListItemResponse::from).toList()
        );
    }
}
