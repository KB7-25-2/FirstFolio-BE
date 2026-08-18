package org.firstfolio.gifticon.dto.response;

import org.firstfolio.gifticon.domain.GifticonCode;

import java.time.LocalDateTime;

public record AdminGifticonCodeResponse(
        Long gifticonCodeId,
        Long gifticonProductId,
        String codeMasked,
        String status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
    public static AdminGifticonCodeResponse from(GifticonCode code) {
        return new AdminGifticonCodeResponse(
                code.getGifticonCodeId(), code.getGifticonProductId(), code.getCodeMasked(),
                code.getStatus(), code.getExpiresAt(), code.getCreatedAt()
        );
    }
}
