package org.firstfolio.gifticon.dto.response;

import java.time.LocalDateTime;

public record MyGifticonResponse(
        Long gifticonOrderId,
        long gifticonProductId,
        String brandName,
        String productName,
        String category,
        int faceValueKrw,
        int spentPoints,
        String imageUrl,
        String codeMasked,
        LocalDateTime expiresAt,
        LocalDateTime firstDisclosedAt,
        LocalDateTime completedAt
) { }
