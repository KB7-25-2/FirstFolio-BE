package org.firstfolio.portfolio.dto.request;

import java.time.LocalDateTime;

/**
 * 자산 이벤트 처리 요청 (API_DOCS {@code POST /internal/portfolio-events/process}).
 *
 * <p><b>주기는 이 서버가 정하지 않는다.</b> 스케줄러가 부르는 시점이 곧 기준이고, 서버는
 * 요청받은 {@code process_until}까지 도래한 이벤트를 반영할 뿐이다 (가격 갱신과 같은 방식).</p>
 */
public class PortfolioEventProcessRequest {

    /** 이 시각까지 도래한 이벤트를 처리한다. 미래 시각은 거부한다. */
    private LocalDateTime processUntil;

    /** 비우면 기본값을 쓴다. 남은 건은 다음 호출에서 이어서 처리된다. */
    private Integer batchSize;

    public LocalDateTime getProcessUntil() {
        return processUntil;
    }

    public void setProcessUntil(LocalDateTime processUntil) {
        this.processUntil = processUntil;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }
}
