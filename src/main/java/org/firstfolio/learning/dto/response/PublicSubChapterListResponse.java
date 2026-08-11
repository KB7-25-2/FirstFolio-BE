package org.firstfolio.learning.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.PublicSubChapter;

import java.util.List;

@Schema(description = "사용자용 공개 소단원 목록")
public record PublicSubChapterListResponse(
        List<PublicSubChapterListItemResponse> items
) {
    public static PublicSubChapterListResponse from(List<PublicSubChapter> chapters) {
        return new PublicSubChapterListResponse(
                chapters.stream()
                        .map(PublicSubChapterListItemResponse::from)
                        .toList()
        );
    }
}
