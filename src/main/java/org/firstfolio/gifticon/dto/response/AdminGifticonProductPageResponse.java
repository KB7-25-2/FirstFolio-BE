package org.firstfolio.gifticon.dto.response;

import java.util.List;

public record AdminGifticonProductPageResponse(
        List<AdminGifticonProductResponse> items,
        String nextCursor
) { }
