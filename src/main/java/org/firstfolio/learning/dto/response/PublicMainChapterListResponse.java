package org.firstfolio.learning.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.MainChapter;

import java.util.List;

@Schema(description = "사용자용 공개 대단원 목록")
public record PublicMainChapterListResponse(
        List<PublicMainChapterListItemResponse> items
) {
    public static PublicMainChapterListResponse from(List<MainChapter> chapters) {
        return new PublicMainChapterListResponse(
                chapters.stream()
                        .map(PublicMainChapterListItemResponse::from)
                        .toList()
        );
    }
}
