package org.firstfolio.gifticon.dto.response;

import java.util.List;

public record MyGifticonPageResponse(
        List<MyGifticonListItemResponse> items,
        String nextCursor
) { }
