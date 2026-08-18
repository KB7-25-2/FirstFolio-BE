package org.firstfolio.gifticon.service;

import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.gifticon.domain.GifticonProduct;
import org.firstfolio.gifticon.dto.request.GifticonProductCreateRequest;
import org.firstfolio.gifticon.dto.response.AdminGifticonProductResponse;
import org.firstfolio.gifticon.mapper.GifticonProductMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GifticonProductAdminServiceTest {

    @Test
    void createsStoppedProductWithPointsEqualToFaceValue() {
        GifticonProductMapper mapper = mock(GifticonProductMapper.class);
        AdminAuditLogMapper audit = mock(AdminAuditLogMapper.class);
        doAnswer(invocation -> {
            invocation.<GifticonProduct>getArgument(0).setGifticonProductId(11L);
            return null;
        }).when(mapper).insert(any(GifticonProduct.class));
        GifticonProductAdminService service = new GifticonProductAdminService(
                mapper, audit,
                Clock.fixed(Instant.parse("2026-08-18T01:00:00Z"), ZoneOffset.UTC)
        );

        AdminGifticonProductResponse response = service.create(
                new GifticonProductCreateRequest(
                        " 아메리카노 ", " 카페 ", "CAFE", 4500, null, null
                ),
                900L,
                "req-product"
        );

        assertEquals(11L, response.gifticonProductId());
        assertEquals(4500, response.faceValueKrw());
        assertEquals(4500, response.requiredPoints());
        assertEquals("STOPPED", response.status());
        verify(audit).insert(
                eq(900L), eq("CREATE"), eq("GIFTICON_PRODUCT"), eq(11L),
                isNull(), anyString(), eq("req-product"), any()
        );
    }
}
