package org.firstfolio.gifticon.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.firstfolio.gifticon.domain.GifticonProduct;
import org.firstfolio.gifticon.domain.GifticonProductInventory;

import java.time.LocalDateTime;

@Schema(description = "관리자용 기프티콘 상품과 재고 현황")
public record AdminGifticonProductResponse(
        Long gifticonProductId,
        String name,
        String brandName,
        String category,
        int faceValueKrw,
        int requiredPoints,
        String status,
        String stockStatus,
        int availableCodeCount,
        int assignedCodeCount,
        int voidCodeCount,
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminGifticonProductResponse from(GifticonProduct product) {
        int available = product instanceof GifticonProductInventory inventory
                ? inventory.getAvailableCodeCount() : 0;
        int assigned = product instanceof GifticonProductInventory inventory
                ? inventory.getAssignedCodeCount() : 0;
        int voidCount = product instanceof GifticonProductInventory inventory
                ? inventory.getVoidCodeCount() : 0;
        return new AdminGifticonProductResponse(
                product.getGifticonProductId(), product.getName(), product.getBrandName(),
                product.getCategory(), product.getFaceValueKrw(), product.getRequiredPoints(),
                product.getStatus(), available > 0 ? "IN_STOCK" : "SOLD_OUT", available,
                assigned, voidCount, product.getImageUrl(), product.getCreatedAt(), product.getUpdatedAt()
        );
    }
}
