package org.firstfolio.curriculum.dto.response;

import org.firstfolio.curriculum.domain.MainChapter;

import java.util.List;

public record MainChapterListResponse(
        List<MainChapterListItemResponse> items
) {
    public static MainChapterListResponse from(List<MainChapter> chapters) {
        return new MainChapterListResponse(
                chapters.stream().map(MainChapterListItemResponse::from).toList()
        );
    }
}
