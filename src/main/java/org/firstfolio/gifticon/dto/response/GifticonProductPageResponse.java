package org.firstfolio.gifticon.dto.response;

import java.util.List;

public record GifticonProductPageResponse(
        int pointBalance,
        List<GifticonProductListItemResponse> items,
        String nextCursor
) { }
