package org.firstfolio.gifticon.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.domain.GifticonProductInventory;
import org.firstfolio.gifticon.dto.response.GifticonProductPageResponse;
import org.firstfolio.gifticon.dto.response.GifticonProductResponse;
import org.firstfolio.gifticon.mapper.GifticonExchangeMapper;
import org.firstfolio.gifticon.mapper.GifticonProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GifticonMarketQueryServiceTest {

    private GifticonProductMapper productMapper;
    private GifticonExchangeMapper exchangeMapper;
    private GifticonMarketQueryService service;

    @BeforeEach
    void setUp() {
        productMapper = mock(GifticonProductMapper.class);
        exchangeMapper = mock(GifticonExchangeMapper.class);
        service = new GifticonMarketQueryService(productMapper, exchangeMapper);
    }

    @Test
    void returnsBalanceAvailabilityAndOpaqueNextCursor() {
        when(exchangeMapper.findPointBalance(101L)).thenReturn(5000);
        when(productMapper.findMarketPage("CAFE", null, 2)).thenReturn(List.of(
                product(11L, 1), product(12L, 0)
        ));

        GifticonProductPageResponse response = service.findPage(
                101L, " CAFE ", null, 1
        );

        assertEquals(5000, response.pointBalance());
        assertEquals(1, response.items().size());
        assertTrue(response.items().get(0).canExchange());
        assertEquals("AVAILABLE", response.items().get(0).stockStatus());
        assertEquals("11", response.nextCursor());
    }

    @Test
    void detailReportsSoldOutWithoutExposingQuantity() {
        when(productMapper.findOnSaleInventoryById(11L)).thenReturn(product(11L, 0));
        when(exchangeMapper.findPointBalance(101L)).thenReturn(9000);

        GifticonProductResponse response = service.findById(101L, 11L);

        assertEquals("SOLD_OUT", response.stockStatus());
        assertFalse(response.canExchange());
    }

    @Test
    void stoppedOrMissingProductIsNotFound() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findById(101L, 11L)
        );

        assertEquals(ErrorCode.GIFTICON_PRODUCT_NOT_FOUND, exception.getErrorCode());
        verify(exchangeMapper, never()).findPointBalance(101L);
    }

    @Test
    void rejectsInvalidCursorBeforeReadingUserOrProducts() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.findPage(101L, null, "not-a-number", 20)
        );

        assertEquals(ErrorCode.INVALID_GIFTICON_FILTER, exception.getErrorCode());
        verify(exchangeMapper, never()).findPointBalance(101L);
        verify(productMapper, never()).findMarketPage(null, null, 21);
    }

    private GifticonProductInventory product(long productId, int availableCodeCount) {
        GifticonProductInventory product = new GifticonProductInventory();
        product.setGifticonProductId(productId);
        product.setName("아메리카노 교환권");
        product.setBrandName("스타카페");
        product.setCategory("CAFE");
        product.setFaceValueKrw(5000);
        product.setRequiredPoints(5000);
        product.setStatus("ON_SALE");
        product.setAvailableCodeCount(availableCodeCount);
        return product;
    }
}
