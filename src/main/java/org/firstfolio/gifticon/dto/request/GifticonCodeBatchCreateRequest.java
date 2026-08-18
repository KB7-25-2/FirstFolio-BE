package org.firstfolio.gifticon.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "선구매 기프티콘 코드 일괄 등록 요청. 한 번에 최대 100개")
public record GifticonCodeBatchCreateRequest(
        List<GifticonCodeCreateItemRequest> items
) { }
