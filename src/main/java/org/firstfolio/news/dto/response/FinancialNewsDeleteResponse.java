package org.firstfolio.news.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "금융 뉴스 삭제 결과")
public record FinancialNewsDeleteResponse(
        @Schema(description = "삭제한 금융 뉴스 식별자", example = "1")
        long financialNewsId
) {
}
