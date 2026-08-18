package org.firstfolio.gifticon.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "포인트 기프티콘 교환 요청")
public record GifticonExchangeRequest(
        Long gifticonProductId
) { }
