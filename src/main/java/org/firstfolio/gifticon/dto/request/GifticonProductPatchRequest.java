package org.firstfolio.gifticon.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기프티콘 상품 부분 수정 요청")
public record GifticonProductPatchRequest(
        String name,
        String brandName,
        String category,
        Integer faceValueKrw,
        String imageUrl,
        String status
) { }
