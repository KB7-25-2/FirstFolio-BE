package org.firstfolio.curriculum.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소단원 생성 정보")
public record SubChapterCreateRequest(
        @Schema(description = "소단원 제목", example = "예금과 적금의 차이") String title,
        @Schema(description = "소단원 소개", example = "두 상품의 목적과 구조를 비교합니다.") String description,
        @Schema(description = "대단원 안에서의 노출 순서", example = "1") Integer displayOrder
) {
}
