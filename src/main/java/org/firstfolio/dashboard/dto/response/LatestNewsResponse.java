package org.firstfolio.dashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 대시보드 latest_news 섹션 한 건.
 *
 * <p>FE {@code dashboardService.js}가 이 섹션을 배열 그대로 기대하고 바로 {@code .map()}을
 * 호출하기 때문에, {@code available}/{@code reason} 래핑 없이 JSON 최상위에 배열로 나간다
 * (뉴스 도메인이 없어 지금은 항상 빈 배열).</p>
 *
 * <p>{@code knowledgeContentId} 필드명은 FE mock을 잠정 사용한 것이다. 실제 식별자 체계는
 * AI↔BE 연동 트랙에서 확정한다 — 이 트랙 단독으로 정하지 않는다.</p>
 */
@Schema(description = "뉴스 한 건")
public final class LatestNewsResponse {

    @Schema(description = "잠정 식별자명 — 이름 확정 전", example = "1001")
    private final Long knowledgeContentId;
    @Schema(description = "제목")
    private final String title;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "기준 일시")
    private final LocalDateTime referenceAt;

    public LatestNewsResponse(Long knowledgeContentId, String title, LocalDateTime referenceAt) {
        this.knowledgeContentId = knowledgeContentId;
        this.title = title;
        this.referenceAt = referenceAt;
    }

    public Long getKnowledgeContentId() {
        return knowledgeContentId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getReferenceAt() {
        return referenceAt;
    }
}
