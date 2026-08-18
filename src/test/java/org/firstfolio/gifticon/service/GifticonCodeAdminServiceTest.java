package org.firstfolio.gifticon.service;

import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.crypto.GifticonCryptoService;
import org.firstfolio.gifticon.domain.GifticonCode;
import org.firstfolio.gifticon.domain.GifticonProduct;
import org.firstfolio.gifticon.dto.request.GifticonCodeBatchCreateRequest;
import org.firstfolio.gifticon.dto.request.GifticonCodeCreateItemRequest;
import org.firstfolio.gifticon.dto.request.GifticonCodeVoidRequest;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodeBatchResponse;
import org.firstfolio.gifticon.mapper.GifticonCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GifticonCodeAdminServiceTest {

    private GifticonCodeMapper mapper;
    private GifticonProductAdminService productService;
    private AdminAuditLogMapper audit;
    private GifticonCodeAdminService service;

    @BeforeEach
    void setUp() {
        mapper = mock(GifticonCodeMapper.class);
        productService = mock(GifticonProductAdminService.class);
        audit = mock(AdminAuditLogMapper.class);
        when(productService.requireProduct(11L)).thenReturn(new GifticonProduct());
        doAnswer(invocation -> {
            GifticonCode code = invocation.getArgument(0);
            code.setGifticonCodeId(100L);
            return null;
        }).when(mapper).insert(any(GifticonCode.class));
        service = new GifticonCodeAdminService(
                mapper,
                productService,
                new GifticonCryptoServiceForTest(),
                audit,
                Clock.fixed(Instant.parse("2026-08-18T01:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void storesOnlyProtectedCodeMaterialAndAuditsMaskedValue() {
        AdminGifticonCodeBatchResponse response = service.createBatch(
                11L,
                new GifticonCodeBatchCreateRequest(List.of(
                        new GifticonCodeCreateItemRequest(
                                "1234-5678-ABCD", LocalDateTime.of(2026, 9, 1, 0, 0)
                        )
                )),
                900L,
                "req-stock"
        );

        ArgumentCaptor<GifticonCode> codeCaptor = ArgumentCaptor.forClass(GifticonCode.class);
        verify(mapper).insert(codeCaptor.capture());
        GifticonCode stored = codeCaptor.getValue();
        assertFalse(new String(stored.getCodeCiphertext()).contains("1234-5678-ABCD"));
        assertEquals(32, stored.getCodeFingerprint().length);
        assertEquals("********ABCD", stored.getCodeMasked());
        assertEquals("AVAILABLE", stored.getStatus());
        assertEquals(1, response.createdCount());

        ArgumentCaptor<String> auditJson = ArgumentCaptor.forClass(String.class);
        verify(audit).insert(
                eq(900L), eq("STOCK_IN"), eq("GIFTICON_CODE_BATCH"), eq(11L),
                any(), auditJson.capture(), eq("req-stock"), any()
        );
        assertTrue(auditJson.getValue().contains("********ABCD"));
        assertFalse(auditJson.getValue().contains("1234-5678-ABCD"));
    }

    @Test
    void rejectsNormalizedDuplicateInsideOneBatchBeforeInsert() {
        ApiException exception = assertThrows(ApiException.class, () -> service.createBatch(
                11L,
                new GifticonCodeBatchCreateRequest(List.of(
                        new GifticonCodeCreateItemRequest("abcd-1234", LocalDateTime.of(2026, 9, 1, 0, 0)),
                        new GifticonCodeCreateItemRequest("ABCD 1234", LocalDateTime.of(2026, 9, 2, 0, 0))
                )),
                900L,
                "req-duplicate"
        ));

        assertEquals(ErrorCode.GIFTICON_CODE_DUPLICATE, exception.getErrorCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    void neverVoidsAlreadyAssignedCode() {
        GifticonCode assigned = new GifticonCode();
        assigned.setGifticonCodeId(100L);
        assigned.setStatus("ASSIGNED");
        when(mapper.findById(100L)).thenReturn(assigned);

        ApiException exception = assertThrows(ApiException.class, () -> service.voidCode(
                100L, new GifticonCodeVoidRequest("잘못 매입된 코드"), 900L, "req-void"
        ));

        assertEquals(ErrorCode.GIFTICON_CODE_ALREADY_ASSIGNED, exception.getErrorCode());
        verify(mapper, never()).markVoidIfAvailable(anyLong());
    }

    @Test
    void convertsExpiresBeforeOffsetToUtcForDatabaseQuery() {
        when(mapper.findPage(
                eq(11L), eq("AVAILABLE"),
                eq(LocalDateTime.of(2026, 8, 31, 15, 0)),
                eq(99L), eq(51)
        )).thenReturn(List.of());

        service.findPage(
                11L, "available", "2026-09-01T00:00:00+09:00", "99", null
        );

        verify(mapper).findPage(
                11L, "AVAILABLE", LocalDateTime.of(2026, 8, 31, 15, 0), 99L, 51
        );
    }

    /** 테스트가 보안 구현의 내부 생성자를 의존하지 않도록 공개 API만 흉내 낸다. */
    private static final class GifticonCryptoServiceForTest extends GifticonCryptoService {
        private GifticonCryptoServiceForTest() {
            super(
                    java.util.Base64.getEncoder().encodeToString(new byte[32]),
                    java.util.Base64.getEncoder().encodeToString(fingerprintKey()),
                    "v1"
            );
        }

        private static byte[] fingerprintKey() {
            byte[] key = new byte[32];
            java.util.Arrays.fill(key, (byte) 1);
            return key;
        }
    }
}
