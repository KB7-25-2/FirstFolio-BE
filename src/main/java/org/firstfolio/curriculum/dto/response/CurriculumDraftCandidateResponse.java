package org.firstfolio.curriculum.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.CurriculumDraftCandidate;

@Schema(description = "개인 커리큘럼 추가 후보")
public record CurriculumDraftCandidateResponse(
        @Schema(description = "대단원 ID", example = "3") long mainChapterId,
        @Schema(description = "대단원명", example = "주식") String title
) {
    public static CurriculumDraftCandidateResponse from(
            CurriculumDraftCandidate candidate
    ) {
        return new CurriculumDraftCandidateResponse(
                candidate.mainChapterId(),
                candidate.title()
        );
    }
}
