package org.firstfolio.gifticon.dto.response;

import java.time.LocalDateTime;

public record GifticonCodeDisclosureResponse(
        long gifticonOrderId,
        String code,
        String barcodeValue,
        String barcodeFormat,
        LocalDateTime expiresAt,
        boolean isExpired,
        LocalDateTime firstDisclosedAt
) { }
