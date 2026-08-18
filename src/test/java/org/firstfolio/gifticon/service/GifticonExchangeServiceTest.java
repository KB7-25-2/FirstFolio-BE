package org.firstfolio.gifticon.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.domain.GifticonCode;
import org.firstfolio.gifticon.domain.GifticonOrder;
import org.firstfolio.gifticon.domain.GifticonOrderView;
import org.firstfolio.gifticon.domain.GifticonProduct;
import org.firstfolio.gifticon.dto.request.GifticonExchangeRequest;
import org.firstfolio.gifticon.dto.response.GifticonExchangeResponse;
import org.firstfolio.gifticon.mapper.GifticonExchangeMapper;
import org.firstfolio.gifticon.mapper.GifticonProductMapper;
import org.firstfolio.reward.domain.PointTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GifticonExchangeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 7, 30);

    private GifticonExchangeMapper exchangeMapper;
    private GifticonProductMapper productMapper;
    private GifticonProductSnapshotCodec snapshotCodec;
    private GifticonExchangeService service;

    @BeforeEach
    void setUp() {
        exchangeMapper = mock(GifticonExchangeMapper.class);
        productMapper = mock(GifticonProductMapper.class);
        snapshotCodec = mock(GifticonProductSnapshotCodec.class);
        service = new GifticonExchangeService(
                exchangeMapper, productMapper, snapshotCodec,
                Clock.fixed(Instant.parse("2026-08-18T07:30:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void exchangesPointsAndCodeInOneServiceTransaction() {
        prepareSuccessfulExchange();

        GifticonExchangeResponse response = service.exchange(
                101L, "exchange-101-1", new GifticonExchangeRequest(11L)
        );

        assertEquals(501L, response.gifticonOrderId());
        assertEquals(5000, response.spentPoints());
        assertEquals(2200, response.pointBalance());
        assertFalse(response.idempotentReplay());
        verify(exchangeMapper).findPointBalanceForUpdate(101L);
        verify(productMapper).findById(11L);
        verify(exchangeMapper).lockNextAvailableCode(11L, NOW);
        verify(exchangeMapper).assignCode(301L);
        verify(exchangeMapper).decreasePointBalance(101L, 5000, NOW);

        ArgumentCaptor<PointTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PointTransaction.class);
        verify(exchangeMapper).insertPointTransaction(transactionCaptor.capture());
        PointTransaction transaction = transactionCaptor.getValue();
        assertEquals("USE", transaction.getTransactionType());
        assertEquals(-5000, transaction.getAmount());
        assertEquals("GIFTICON", transaction.getReasonType());
        assertEquals(2200, transaction.getBalanceAfter());
        assertTrue(transaction.getIdempotencyKey().startsWith("gifticon:"));
        assertTrue(transaction.getIdempotencyKey().length() <= 120);

        ArgumentCaptor<GifticonOrder> orderCaptor = ArgumentCaptor.forClass(GifticonOrder.class);
        verify(exchangeMapper).insertOrder(orderCaptor.capture());
        GifticonOrder order = orderCaptor.getValue();
        assertEquals(301L, order.getGifticonCodeId());
        assertEquals(401L, order.getPointTransactionId());
        assertEquals("exchange-101-1", order.getIdempotencyKey());
        assertEquals("{\"gifticon_product_id\":11}", order.getProductSnapshotJson());
        assertNotNull(order.getRequestFingerprint());
        verify(exchangeMapper).linkPointTransactionReason(401L, 501L);
    }

    @Test
    void returnsExistingOrderWithoutSecondPointOrCodeMutation() throws Exception {
        GifticonOrderView existing = existingOrder(fingerprint(11L));
        when(exchangeMapper.findPointBalanceForUpdate(101L)).thenReturn(2200);
        when(exchangeMapper.findByUserAndIdempotency(101L, "exchange-101-1"))
                .thenReturn(existing);

        GifticonExchangeResponse response = service.exchange(
                101L, "exchange-101-1", new GifticonExchangeRequest(11L)
        );

        assertTrue(response.idempotentReplay());
        assertEquals(501L, response.gifticonOrderId());
        assertEquals(2200, response.pointBalance());
        verify(productMapper, never()).findById(11L);
        verify(exchangeMapper, never()).lockNextAvailableCode(anyLong(), any());
        verify(exchangeMapper, never()).decreasePointBalance(eq(101L), eq(5000), any());
    }

    @Test
    void rejectsSameIdempotencyKeyForDifferentProduct() {
        GifticonOrderView existing = existingOrder(new byte[32]);
        when(exchangeMapper.findPointBalanceForUpdate(101L)).thenReturn(2200);
        when(exchangeMapper.findByUserAndIdempotency(101L, "same-key"))
                .thenReturn(existing);

        ApiException exception = assertThrows(ApiException.class, () -> service.exchange(
                101L, "same-key", new GifticonExchangeRequest(12L)
        ));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, exception.getErrorCode());
        verify(exchangeMapper, never()).decreasePointBalance(eq(101L), anyInt(), any());
    }

    @Test
    void rejectsInsufficientPointsBeforeLockingCode() {
        when(exchangeMapper.findPointBalanceForUpdate(101L)).thenReturn(4999);
        when(productMapper.findById(11L)).thenReturn(product());

        ApiException exception = assertThrows(ApiException.class, () -> service.exchange(
                101L, "poor-user", new GifticonExchangeRequest(11L)
        ));

        assertEquals(ErrorCode.INSUFFICIENT_POINTS, exception.getErrorCode());
        verify(exchangeMapper, never()).lockNextAvailableCode(anyLong(), any());
    }

    @Test
    void distinguishesStoppedProductFromMissingProduct() {
        GifticonProduct stopped = product();
        stopped.setStatus("STOPPED");
        when(exchangeMapper.findPointBalanceForUpdate(101L)).thenReturn(7200);
        when(productMapper.findById(11L)).thenReturn(stopped);

        ApiException exception = assertThrows(ApiException.class, () -> service.exchange(
                101L, "stopped", new GifticonExchangeRequest(11L)
        ));

        assertEquals(ErrorCode.GIFTICON_NOT_ON_SALE, exception.getErrorCode());
    }

    @Test
    void confirmsSoldOutWithWaitingLockAfterSkipLockedFindsNothing() {
        when(exchangeMapper.findPointBalanceForUpdate(101L)).thenReturn(7200);
        when(productMapper.findById(11L)).thenReturn(product());

        ApiException exception = assertThrows(ApiException.class, () -> service.exchange(
                101L, "sold-out", new GifticonExchangeRequest(11L)
        ));

        assertEquals(ErrorCode.GIFTICON_SOLD_OUT, exception.getErrorCode());
        verify(exchangeMapper).lockNextAvailableCode(11L, NOW);
        verify(exchangeMapper).lockNextAvailableCodeWaiting(11L, NOW);
        verify(exchangeMapper, never()).decreasePointBalance(eq(101L), anyInt(), any());
    }

    private void prepareSuccessfulExchange() {
        when(exchangeMapper.findPointBalanceForUpdate(101L)).thenReturn(7200);
        when(productMapper.findById(11L)).thenReturn(product());
        GifticonCode code = new GifticonCode();
        code.setGifticonCodeId(301L);
        code.setGifticonProductId(11L);
        code.setCodeMasked("********9012");
        code.setExpiresAt(LocalDateTime.of(2027, 2, 28, 14, 59, 59));
        code.setStatus("AVAILABLE");
        when(exchangeMapper.lockNextAvailableCode(11L, NOW)).thenReturn(code);
        when(exchangeMapper.assignCode(301L)).thenReturn(1);
        when(exchangeMapper.decreasePointBalance(101L, 5000, NOW)).thenReturn(1);
        when(snapshotCodec.encode(any())).thenReturn("{\"gifticon_product_id\":11}");
        doAnswer(invocation -> {
            invocation.<PointTransaction>getArgument(0).setPointTransactionId(401L);
            return null;
        }).when(exchangeMapper).insertPointTransaction(any());
        doAnswer(invocation -> {
            invocation.<GifticonOrder>getArgument(0).setGifticonOrderId(501L);
            return null;
        }).when(exchangeMapper).insertOrder(any());
        when(exchangeMapper.linkPointTransactionReason(401L, 501L)).thenReturn(1);
    }

    private GifticonProduct product() {
        GifticonProduct product = new GifticonProduct();
        product.setGifticonProductId(11L);
        product.setName("아메리카노 교환권");
        product.setBrandName("스타카페");
        product.setCategory("CAFE");
        product.setFaceValueKrw(5000);
        product.setRequiredPoints(5000);
        product.setStatus("ON_SALE");
        return product;
    }

    private GifticonOrderView existingOrder(byte[] requestFingerprint) {
        GifticonOrderView order = new GifticonOrderView();
        order.setGifticonOrderId(501L);
        order.setGifticonProductId(11L);
        order.setSpentPoints(5000);
        order.setBalanceAfter(2200);
        order.setCompletedAt(NOW);
        order.setRequestFingerprint(requestFingerprint);
        return order;
    }

    private byte[] fingerprint(long productId) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(
                ("gifticon_product_id:" + productId).getBytes(StandardCharsets.UTF_8)
        );
    }
}
