package org.firstfolio.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 원천 데이터에서 모의 상품을 가져오는 요청.
 *
 * <p><b>API_DOCS.md와 다른 점</b>: 문서의 요청 본문에는 {@code items} 배열이 있어 관리자가
 * 상품별 조건({@code real_terms}, {@code simulation_terms})을 직접 담아 보내게 되어 있다.
 * 실제 흐름은 <b>서버가 외부 API를 호출해 조건을 채우고 압축까지 계산</b>하므로 관리자가
 * 보낼 값이 없다. 그래서 {@code items}를 받지 않는다.</p>
 *
 * <p>가명({@code display_name})은 등록 후 {@code PATCH}로 입력한다. 등록 시점에는 비공개
 * 상태이므로 사용자에게 노출되지 않는다.</p>
 */
@Schema(description = "외부 원천에서 모의 상품을 수집하는 요청. 상품 목록은 서버가 조회하므로 items를 받지 않음")
public class ProductImportRequest {

    @Schema(description = "수집할 외부 제공기관", example = "FINLIFE")
    private String sourceProvider;
    @Schema(description = "원천 데이터 기준 시각", example = "2026-08-07T09:00:00")
    private LocalDateTime referenceAt;

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public LocalDateTime getReferenceAt() {
        return referenceAt;
    }

    public void setReferenceAt(LocalDateTime referenceAt) {
        this.referenceAt = referenceAt;
    }
}
