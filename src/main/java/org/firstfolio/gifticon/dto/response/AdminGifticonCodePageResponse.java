package org.firstfolio.gifticon.dto.response;

import java.util.List;

public record AdminGifticonCodePageResponse(
        List<AdminGifticonCodeResponse> items,
        String nextCursor
) { }
