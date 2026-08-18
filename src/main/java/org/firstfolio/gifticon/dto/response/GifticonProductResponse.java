package org.firstfolio.gifticon.dto.response;

import org.firstfolio.gifticon.domain.GifticonProductInventory;

public record GifticonProductResponse(
        Long gifticonProductId,
        String name,
        String brandName,
        String category,
        int faceValueKrw,
        int requiredPoints,
        String stockStatus,
        int pointBalance,
        boolean canExchange,
        String imageUrl
) {
    public static GifticonProductResponse from(
            GifticonProductInventory product,
            int pointBalance
    ) {
        boolean available = product.getAvailableCodeCount() > 0;
        return new GifticonProductResponse(
                product.getGifticonProductId(), product.getName(), product.getBrandName(),
                product.getCategory(), product.getFaceValueKrw(), product.getRequiredPoints(),
                available ? "AVAILABLE" : "SOLD_OUT", pointBalance,
                available && pointBalance >= product.getRequiredPoints(), product.getImageUrl()
        );
    }
}
