package org.firstfolio.gifticon.dto.response;

import org.firstfolio.gifticon.domain.GifticonProductInventory;

public record GifticonProductListItemResponse(
        Long gifticonProductId,
        String name,
        String brandName,
        String category,
        int faceValueKrw,
        int requiredPoints,
        String stockStatus,
        boolean canExchange,
        String imageUrl
) {
    public static GifticonProductListItemResponse from(
            GifticonProductInventory product,
            int pointBalance
    ) {
        boolean available = product.getAvailableCodeCount() > 0;
        return new GifticonProductListItemResponse(
                product.getGifticonProductId(), product.getName(), product.getBrandName(),
                product.getCategory(), product.getFaceValueKrw(), product.getRequiredPoints(),
                available ? "AVAILABLE" : "SOLD_OUT",
                available && pointBalance >= product.getRequiredPoints(), product.getImageUrl()
        );
    }
}
