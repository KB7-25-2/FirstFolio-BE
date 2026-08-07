package org.firstfolio.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.user.domain.PointBalanceSnapshot;

import java.time.LocalDateTime;

@Schema(description = "서비스 포인트 잔액. 모의투자금과는 다른 재화")
public record PointBalanceResponse(
        @Schema(description = "현재 포인트 잔액", example = "1250") int pointBalance,
        @Schema(description = "잔액 기준 시각", example = "2026-08-07T10:15:00") LocalDateTime updatedAt
) {
    public static PointBalanceResponse from(PointBalanceSnapshot snapshot) {
        return new PointBalanceResponse(
                snapshot.getPointBalance(), snapshot.getUpdatedAt()
        );
    }
}
