package org.firstfolio.gifticon.dto.response;

import java.util.List;

public record AdminGifticonCodeBatchResponse(
        int createdCount,
        List<AdminGifticonCodeResponse> items
) { }
