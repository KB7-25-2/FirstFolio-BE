package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.CurriculumDraftResult;

import java.util.List;

@Schema(description = "레벨 테스트 기반 개인 커리큘럼 기본 초안")
public record CurriculumDraftResponse(
        @Schema(description = "FOUNDATION과 기본 추천 항목") List<CurriculumDraftItemResponse> items,
        @Schema(description = "오답 추천 대단원 후보") List<CurriculumDraftCandidateResponse> recommendationCandidates,
        @Schema(description = "전체 정답 대단원 추가 후보") List<CurriculumDraftCandidateResponse> cartCandidates
) {
    public CurriculumDraftResponse {
        items = List.copyOf(items);
        recommendationCandidates = List.copyOf(recommendationCandidates);
        cartCandidates = List.copyOf(cartCandidates);
    }

    public static CurriculumDraftResponse from(CurriculumDraftResult result) {
        return new CurriculumDraftResponse(
                result.items().stream()
                        .map(CurriculumDraftItemResponse::from)
                        .toList(),
                result.recommendationCandidates().stream()
                        .map(CurriculumDraftCandidateResponse::from)
                        .toList(),
                result.cartCandidates().stream()
                        .map(CurriculumDraftCandidateResponse::from)
                        .toList()
        );
    }
}
