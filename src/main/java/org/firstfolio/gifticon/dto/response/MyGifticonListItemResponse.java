package org.firstfolio.gifticon.dto.response;

import java.time.LocalDateTime;

public record MyGifticonListItemResponse(
        Long gifticonOrderId,
        long gifticonProductId,
        String brandName,
        String productName,
        int spentPoints,
        String codeMasked,
        LocalDateTime expiresAt,
        boolean isDisclosed,
        LocalDateTime completedAt
) { }
