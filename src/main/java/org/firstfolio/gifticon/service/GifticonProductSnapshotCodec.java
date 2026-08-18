package org.firstfolio.gifticon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.domain.GifticonProduct;
import org.firstfolio.gifticon.domain.GifticonProductSnapshot;
import org.springframework.stereotype.Component;

@Component
public class GifticonProductSnapshotCodec {

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    public String encode(GifticonProduct product) {
        return write(new GifticonProductSnapshot(
                product.getGifticonProductId(), product.getName(), product.getBrandName(),
                product.getCategory(), product.getFaceValueKrw(), product.getRequiredPoints(),
                product.getImageUrl()
        ));
    }

    public GifticonProductSnapshot decode(String json) {
        if (json == null || json.isBlank()) throw internal(null);
        try {
            GifticonProductSnapshot snapshot = objectMapper.readValue(
                    json, GifticonProductSnapshot.class
            );
            if (snapshot.gifticonProductId() <= 0
                    || snapshot.name() == null
                    || snapshot.brandName() == null
                    || snapshot.requiredPoints() <= 0) {
                throw internal(null);
            }
            return snapshot;
        } catch (JsonProcessingException exception) {
            throw internal(exception);
        }
    }

    private String write(GifticonProductSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw internal(exception);
        }
    }

    private ApiException internal(Throwable cause) {
        return new ApiException(
                ErrorCode.INTERNAL_ERROR,
                "기프티콘 상품 스냅샷을 처리할 수 없습니다.",
                cause
        );
    }
}
