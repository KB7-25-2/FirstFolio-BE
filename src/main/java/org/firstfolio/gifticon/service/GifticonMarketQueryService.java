package org.firstfolio.gifticon.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.domain.GifticonProductInventory;
import org.firstfolio.gifticon.dto.response.GifticonProductPageResponse;
import org.firstfolio.gifticon.dto.response.GifticonProductResponse;
import org.firstfolio.gifticon.dto.response.GifticonProductListItemResponse;
import org.firstfolio.gifticon.mapper.GifticonProductMapper;
import org.firstfolio.gifticon.mapper.GifticonExchangeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GifticonMarketQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final GifticonProductMapper productMapper;
    private final GifticonExchangeMapper exchangeMapper;

    public GifticonMarketQueryService(
            GifticonProductMapper productMapper,
            GifticonExchangeMapper exchangeMapper
    ) {
        this.productMapper = productMapper;
        this.exchangeMapper = exchangeMapper;
    }

    @Transactional(readOnly = true)
    public GifticonProductPageResponse findPage(
            long userId,
            String category,
            String cursor,
            Integer size
    ) {
        int pageSize = pageSize(size);
        String normalizedCategory = normalizeCategory(category);
        Long parsedCursor = parseCursor(cursor);
        int pointBalance = pointBalance(userId);
        List<GifticonProductInventory> found = productMapper.findMarketPage(
                normalizedCategory, parsedCursor, pageSize + 1
        );
        boolean hasNext = found.size() > pageSize;
        List<GifticonProductInventory> page = hasNext ? found.subList(0, pageSize) : found;
        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getGifticonProductId()) : null;
        return new GifticonProductPageResponse(
                pointBalance,
                page.stream()
                        .map(product -> GifticonProductListItemResponse.from(
                                product, pointBalance
                        ))
                        .toList(),
                nextCursor
        );
    }

    @Transactional(readOnly = true)
    public GifticonProductResponse findById(long userId, long productId) {
        GifticonProductInventory product = productMapper.findOnSaleInventoryById(productId);
        if (product == null) throw new ApiException(ErrorCode.GIFTICON_PRODUCT_NOT_FOUND);
        return GifticonProductResponse.from(product, pointBalance(userId));
    }

    private int pointBalance(long userId) {
        Integer balance = exchangeMapper.findPointBalance(userId);
        if (balance == null || balance < 0) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return balance;
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return null;
        String value = category.trim();
        if (value.length() > 50) throw new ApiException(ErrorCode.INVALID_GIFTICON_FILTER);
        return value;
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            long value = Long.parseLong(cursor.trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.INVALID_GIFTICON_FILTER);
        }
    }

    private static int pageSize(Integer size) {
        if (size == null) return DEFAULT_PAGE_SIZE;
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.INVALID_GIFTICON_FILTER);
        }
        return size;
    }
}
