package org.firstfolio.gifticon.dto.response;

import java.time.LocalDateTime;

public record GifticonExchangeResponse(
        Long gifticonOrderId,
        long gifticonProductId,
        int spentPoints,
        int pointBalance,
        LocalDateTime completedAt,
        boolean idempotentReplay
) { }
