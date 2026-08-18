package org.firstfolio.gifticon.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.crypto.GifticonCryptoService;
import org.firstfolio.gifticon.domain.GifticonDisclosureData;
import org.firstfolio.gifticon.domain.GifticonOrderView;
import org.firstfolio.gifticon.domain.GifticonProductSnapshot;
import org.firstfolio.gifticon.dto.response.GifticonCodeDisclosureResponse;
import org.firstfolio.gifticon.dto.response.MyGifticonPageResponse;
import org.firstfolio.gifticon.dto.response.MyGifticonResponse;
import org.firstfolio.gifticon.dto.response.MyGifticonListItemResponse;
import org.firstfolio.gifticon.mapper.GifticonExchangeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MyGifticonService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final GifticonExchangeMapper exchangeMapper;
    private final GifticonProductSnapshotCodec snapshotCodec;
    private final GifticonCryptoService cryptoService;
    private final Clock clock;

    public MyGifticonService(
            GifticonExchangeMapper exchangeMapper,
            GifticonProductSnapshotCodec snapshotCodec,
            GifticonCryptoService cryptoService,
            Clock clock
    ) {
        this.exchangeMapper = exchangeMapper;
        this.snapshotCodec = snapshotCodec;
        this.cryptoService = cryptoService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyGifticonPageResponse findPage(long userId, String cursor, Integer size) {
        int pageSize = pageSize(size);
        List<GifticonOrderView> found = exchangeMapper.findOrdersByUser(
                userId, parseCursor(cursor), pageSize + 1
        );
        boolean hasNext = found.size() > pageSize;
        List<GifticonOrderView> page = hasNext ? found.subList(0, pageSize) : found;
        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getGifticonOrderId()) : null;
        return new MyGifticonPageResponse(
                page.stream().map(this::listResponse).toList(), nextCursor
        );
    }

    @Transactional(readOnly = true)
    public MyGifticonResponse findById(long userId, long orderId) {
        GifticonOrderView order = exchangeMapper.findOrderByUser(userId, orderId);
        if (order == null) throw new ApiException(ErrorCode.GIFTICON_ORDER_NOT_FOUND);
        return detailResponse(order);
    }

    @Transactional
    public GifticonCodeDisclosureResponse disclose(
            long userId,
            long orderId,
            String requestId
    ) {
        GifticonDisclosureData order = exchangeMapper.findDisclosureForUpdate(userId, orderId);
        if (order == null) throw new ApiException(ErrorCode.GIFTICON_ORDER_NOT_FOUND);
        if (!"ASSIGNED".equals(order.getCodeStatus())) throw internal(null);

        String plaintext;
        try {
            plaintext = cryptoService.decrypt(
                    order.getCodeCiphertext(), order.getEncryptionKeyVersion()
            );
        } catch (ApiException exception) {
            if (exception.getErrorCode() == ErrorCode.GIFTICON_CRYPTO_UNAVAILABLE) {
                throw new ApiException(
                        ErrorCode.GIFTICON_CODE_DECRYPTION_FAILED,
                        ErrorCode.GIFTICON_CODE_DECRYPTION_FAILED.getDefaultMessage(),
                        exception
                );
            }
            throw exception;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime firstDisclosedAt = order.getFirstDisclosedAt();
        if (firstDisclosedAt == null) {
            if (exchangeMapper.markFirstDisclosed(orderId, now) != 1) throw internal(null);
            firstDisclosedAt = now;
        }
        exchangeMapper.insertAccessLog(orderId, userId, requestId, now);
        return new GifticonCodeDisclosureResponse(
                orderId, plaintext, GifticonCryptoService.normalize(plaintext),
                "CODE_128", order.getExpiresAt(), !order.getExpiresAt().isAfter(now),
                firstDisclosedAt
        );
    }

    private MyGifticonListItemResponse listResponse(GifticonOrderView order) {
        requireAssigned(order);
        GifticonProductSnapshot product = snapshotCodec.decode(order.getProductSnapshotJson());
        return new MyGifticonListItemResponse(
                order.getGifticonOrderId(), order.getGifticonProductId(),
                product.brandName(), product.name(), order.getSpentPoints(),
                order.getCodeMasked(), order.getExpiresAt(),
                order.getFirstDisclosedAt() != null, order.getCompletedAt()
        );
    }

    private MyGifticonResponse detailResponse(GifticonOrderView order) {
        requireAssigned(order);
        GifticonProductSnapshot product = snapshotCodec.decode(order.getProductSnapshotJson());
        return new MyGifticonResponse(
                order.getGifticonOrderId(), order.getGifticonProductId(),
                product.brandName(), product.name(), product.category(),
                product.faceValueKrw(), order.getSpentPoints(), product.imageUrl(),
                order.getCodeMasked(),
                order.getExpiresAt(), order.getFirstDisclosedAt(), order.getCompletedAt()
        );
    }

    private static void requireAssigned(GifticonOrderView order) {
        if (!"ASSIGNED".equals(order.getCodeStatus()) || order.getExpiresAt() == null) {
            throw internal(null);
        }
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            long value = Long.parseLong(cursor.trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.INVALID_GIFTICON_ORDER_CURSOR);
        }
    }

    private static int pageSize(Integer size) {
        if (size == null) return DEFAULT_PAGE_SIZE;
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.INVALID_GIFTICON_ORDER_CURSOR);
        }
        return size;
    }

    private static ApiException internal(Throwable cause) {
        return new ApiException(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                cause
        );
    }
}
