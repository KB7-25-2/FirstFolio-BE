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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class GifticonExchangeService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final GifticonExchangeMapper exchangeMapper;
    private final GifticonProductMapper productMapper;
    private final GifticonProductSnapshotCodec snapshotCodec;
    private final Clock clock;

    public GifticonExchangeService(
            GifticonExchangeMapper exchangeMapper,
            GifticonProductMapper productMapper,
            GifticonProductSnapshotCodec snapshotCodec,
            Clock clock
    ) {
        this.exchangeMapper = exchangeMapper;
        this.productMapper = productMapper;
        this.snapshotCodec = snapshotCodec;
        this.clock = clock;
    }

    @Transactional
    public GifticonExchangeResponse exchange(
            long userId,
            String idempotencyKey,
            GifticonExchangeRequest request
    ) {
        long productId = requireProductId(request);
        String key = requireIdempotencyKey(idempotencyKey);
        byte[] requestFingerprint = requestFingerprint(productId);
        LocalDateTime now = LocalDateTime.now(clock);

        Integer balance = exchangeMapper.findPointBalanceForUpdate(userId);
        if (balance == null) throw internal(null);

        GifticonOrderView existing = exchangeMapper.findByUserAndIdempotency(userId, key);
        if (existing != null) {
            if (!MessageDigest.isEqual(existing.getRequestFingerprint(), requestFingerprint)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return response(existing);
        }

        GifticonProduct product = productMapper.findById(productId);
        if (product == null) throw new ApiException(ErrorCode.GIFTICON_PRODUCT_NOT_FOUND);
        if (!GifticonProductAdminService.ON_SALE.equals(product.getStatus())) {
            throw new ApiException(ErrorCode.GIFTICON_NOT_ON_SALE);
        }
        if (product.getRequiredPoints() <= 0
                || product.getRequiredPoints() != product.getFaceValueKrw()) {
            throw internal(null);
        }
        if (balance < product.getRequiredPoints()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_POINTS);
        }

        GifticonCode code = exchangeMapper.lockNextAvailableCode(productId, now);
        if (code == null) {
            code = exchangeMapper.lockNextAvailableCodeWaiting(productId, now);
        }
        if (code == null) throw new ApiException(ErrorCode.GIFTICON_SOLD_OUT);
        if (exchangeMapper.assignCode(code.getGifticonCodeId()) != 1) {
            throw new ApiException(ErrorCode.GIFTICON_SOLD_OUT);
        }

        int balanceAfter = balance - product.getRequiredPoints();
        if (exchangeMapper.decreasePointBalance(
                userId, product.getRequiredPoints(), now
        ) != 1) {
            throw new ApiException(ErrorCode.INSUFFICIENT_POINTS);
        }

        PointTransaction pointTransaction = pointTransaction(
                userId, product.getRequiredPoints(), balanceAfter, key, now
        );
        exchangeMapper.insertPointTransaction(pointTransaction);
        if (pointTransaction.getPointTransactionId() == null) throw internal(null);

        GifticonOrder order = order(
                userId, product, code, pointTransaction.getPointTransactionId(),
                key, requestFingerprint, now
        );
        exchangeMapper.insertOrder(order);
        if (order.getGifticonOrderId() == null) throw internal(null);
        if (exchangeMapper.linkPointTransactionReason(
                pointTransaction.getPointTransactionId(), order.getGifticonOrderId()
        ) != 1) {
            throw internal(null);
        }

        return new GifticonExchangeResponse(
                order.getGifticonOrderId(), productId, product.getRequiredPoints(),
                balanceAfter, now, false
        );
    }

    private PointTransaction pointTransaction(
            long userId,
            int spentPoints,
            int balanceAfter,
            String orderIdempotencyKey,
            LocalDateTime now
    ) {
        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType("USE");
        transaction.setAmount(-spentPoints);
        transaction.setReasonType("GIFTICON");
        transaction.setReasonId(null);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setIdempotencyKey(pointIdempotencyKey(userId, orderIdempotencyKey));
        transaction.setOccurredAt(now);
        transaction.setCreatedAt(now);
        return transaction;
    }

    private GifticonOrder order(
            long userId,
            GifticonProduct product,
            GifticonCode code,
            long pointTransactionId,
            String idempotencyKey,
            byte[] requestFingerprint,
            LocalDateTime now
    ) {
        GifticonOrder order = new GifticonOrder();
        order.setUserId(userId);
        order.setGifticonProductId(product.getGifticonProductId());
        order.setGifticonCodeId(code.getGifticonCodeId());
        order.setPointTransactionId(pointTransactionId);
        order.setSpentPoints(product.getRequiredPoints());
        order.setProductSnapshotJson(snapshotCodec.encode(product));
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestFingerprint(requestFingerprint);
        order.setFirstDisclosedAt(null);
        order.setCompletedAt(now);
        return order;
    }

    private GifticonExchangeResponse response(GifticonOrderView order) {
        return new GifticonExchangeResponse(
                order.getGifticonOrderId(), order.getGifticonProductId(),
                order.getSpentPoints(), order.getBalanceAfter(),
                order.getCompletedAt(), true
        );
    }

    private static long requireProductId(GifticonExchangeRequest request) {
        if (request == null
                || request.gifticonProductId() == null
                || request.gifticonProductId() <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "gifticon_product_id는 필수입니다.");
        }
        return request.gifticonProductId();
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Idempotency-Key 헤더는 필수입니다.");
        }
        String value = idempotencyKey.trim();
        if (value.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Idempotency-Key는 100자 이하여야 합니다.");
        }
        return value;
    }

    private static byte[] requestFingerprint(long productId) {
        return sha256("gifticon_product_id:" + productId);
    }

    private static String pointIdempotencyKey(long userId, String orderIdempotencyKey) {
        return "gifticon:" + HexFormat.of().formatHex(
                sha256(userId + ":" + orderIdempotencyKey)
        );
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw internal(exception);
        }
    }

    private static ApiException internal(Throwable cause) {
        return new ApiException(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                cause
        );
    }
}
