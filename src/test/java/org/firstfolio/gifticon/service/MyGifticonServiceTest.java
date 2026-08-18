package org.firstfolio.gifticon.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.crypto.GifticonCryptoService;
import org.firstfolio.gifticon.domain.GifticonDisclosureData;
import org.firstfolio.gifticon.domain.GifticonOrderView;
import org.firstfolio.gifticon.domain.GifticonProductSnapshot;
import org.firstfolio.gifticon.dto.response.GifticonCodeDisclosureResponse;
import org.firstfolio.gifticon.mapper.GifticonExchangeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyGifticonServiceTest {

    private GifticonExchangeMapper mapper;
    private GifticonProductSnapshotCodec snapshotCodec;
    private GifticonCryptoService cryptoService;
    private MyGifticonService service;

    @BeforeEach
    void setUp() {
        mapper = mock(GifticonExchangeMapper.class);
        snapshotCodec = mock(GifticonProductSnapshotCodec.class);
        cryptoService = mock(GifticonCryptoService.class);
        service = new MyGifticonService(
                mapper, snapshotCodec, cryptoService,
                Clock.fixed(Instant.parse("2026-08-18T07:35:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void detailUsesProductSnapshotAndNeverNeedsCiphertext() {
        GifticonOrderView order = order();
        when(mapper.findOrderByUser(101L, 501L)).thenReturn(order);
        when(snapshotCodec.decode(order.getProductSnapshotJson())).thenReturn(snapshot());

        var response = service.findById(101L, 501L);

        assertEquals("스타카페", response.brandName());
        assertEquals("아메리카노 교환권", response.productName());
        assertEquals(5000, response.faceValueKrw());
        assertEquals("********9012", response.codeMasked());
        verify(cryptoService, never()).decrypt(any(), any());
    }

    @Test
    void disclosureDecryptsOwnedCodeAndRecordsFirstAccess() {
        GifticonDisclosureData order = disclosure();
        when(mapper.findDisclosureForUpdate(101L, 501L)).thenReturn(order);
        when(cryptoService.decrypt(order.getCodeCiphertext(), "v1"))
                .thenReturn("1234-5678-9012");
        when(mapper.markFirstDisclosed(
                501L, LocalDateTime.of(2026, 8, 18, 7, 35)
        )).thenReturn(1);

        GifticonCodeDisclosureResponse response = service.disclose(
                101L, 501L, "req-disclose"
        );

        assertEquals("1234-5678-9012", response.code());
        assertEquals("123456789012", response.barcodeValue());
        assertEquals("CODE_128", response.barcodeFormat());
        assertFalse(response.isExpired());
        verify(mapper).markFirstDisclosed(
                501L, LocalDateTime.of(2026, 8, 18, 7, 35)
        );
        verify(mapper).insertAccessLog(
                501L, 101L, "req-disclose", LocalDateTime.of(2026, 8, 18, 7, 35)
        );
    }

    @Test
    void otherUsersOrderIsIndistinguishableFromMissingOrder() {
        ApiException exception = assertThrows(ApiException.class, () ->
                service.findById(202L, 501L)
        );

        assertEquals(ErrorCode.GIFTICON_ORDER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void mapsCryptoFailureToDisclosureSpecificError() {
        GifticonDisclosureData order = disclosure();
        when(mapper.findDisclosureForUpdate(101L, 501L)).thenReturn(order);
        when(cryptoService.decrypt(order.getCodeCiphertext(), "v1")).thenThrow(
                new ApiException(ErrorCode.GIFTICON_CRYPTO_UNAVAILABLE)
        );

        ApiException exception = assertThrows(ApiException.class, () ->
                service.disclose(101L, 501L, "req-disclose")
        );

        assertEquals(ErrorCode.GIFTICON_CODE_DECRYPTION_FAILED, exception.getErrorCode());
        verify(mapper, never()).insertAccessLog(anyLong(), anyLong(), any(), any());
    }

    private GifticonOrderView order() {
        GifticonOrderView order = new GifticonOrderView();
        order.setGifticonOrderId(501L);
        order.setGifticonProductId(11L);
        order.setSpentPoints(5000);
        order.setProductSnapshotJson("snapshot");
        order.setCodeMasked("********9012");
        order.setCodeStatus("ASSIGNED");
        order.setExpiresAt(LocalDateTime.of(2027, 2, 28, 14, 59, 59));
        order.setCompletedAt(LocalDateTime.of(2026, 8, 18, 7, 30));
        return order;
    }

    private GifticonDisclosureData disclosure() {
        GifticonDisclosureData order = new GifticonDisclosureData();
        order.setGifticonOrderId(501L);
        order.setGifticonProductId(11L);
        order.setCodeStatus("ASSIGNED");
        order.setCodeCiphertext(new byte[]{1, 2, 3});
        order.setEncryptionKeyVersion("v1");
        order.setExpiresAt(LocalDateTime.of(2027, 2, 28, 14, 59, 59));
        return order;
    }

    private GifticonProductSnapshot snapshot() {
        return new GifticonProductSnapshot(
                11L, "아메리카노 교환권", "스타카페", "CAFE",
                5000, 5000, "https://example.com/image.png"
        );
    }
}
