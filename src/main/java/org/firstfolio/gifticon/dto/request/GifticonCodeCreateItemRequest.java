package org.firstfolio.gifticon.dto.request;

import java.time.LocalDateTime;

public record GifticonCodeCreateItemRequest(
        String code,
        LocalDateTime expiresAt
) { }
