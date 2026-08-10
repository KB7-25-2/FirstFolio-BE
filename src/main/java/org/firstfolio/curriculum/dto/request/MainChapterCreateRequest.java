package org.firstfolio.curriculum.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.curriculum.domain.AssetType;
import org.firstfolio.curriculum.domain.ChapterType;

@Schema(description = "대단원 생성 정보")
public record MainChapterCreateRequest(
        @Schema(description = "대단원 유형", example = "ASSET") ChapterType chapterType,
        @Schema(description = "자산 대단원의 자산군. 필수 과정이면 생략 가능", example = "DEPOSIT") AssetType assetType,
        @Schema(description = "대단원 제목", example = "예·적금") String title,
        @Schema(description = "대단원 소개", example = "예금과 적금의 기본 원리를 배웁니다.") String description,
        @Schema(description = "노출 순서", example = "1") Integer displayOrder,
        @JsonProperty("is_required") @Schema(description = "모든 사용자 필수 과정 여부", example = "false") Boolean isRequired
) {
}
