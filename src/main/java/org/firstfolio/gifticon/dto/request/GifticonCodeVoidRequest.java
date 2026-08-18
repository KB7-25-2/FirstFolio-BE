package org.firstfolio.gifticon.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "미지급 기프티콘 코드 폐기 요청")
public record GifticonCodeVoidRequest(String reason) { }
