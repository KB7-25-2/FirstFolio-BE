package org.firstfolio.user.dto.response;

import org.firstfolio.user.domain.PointBalanceSnapshot;

import java.time.LocalDateTime;

public record PointBalanceResponse(
        int pointBalance,
        LocalDateTime updatedAt
) {
    public static PointBalanceResponse from(PointBalanceSnapshot snapshot) {
        return new PointBalanceResponse(
                snapshot.getPointBalance(), snapshot.getUpdatedAt()
        );
    }
}
