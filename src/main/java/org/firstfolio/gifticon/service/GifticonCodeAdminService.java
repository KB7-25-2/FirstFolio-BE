package org.firstfolio.gifticon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.crypto.GifticonCryptoService;
import org.firstfolio.gifticon.domain.GifticonCode;
import org.firstfolio.gifticon.dto.request.GifticonCodeBatchCreateRequest;
import org.firstfolio.gifticon.dto.request.GifticonCodeCreateItemRequest;
import org.firstfolio.gifticon.dto.request.GifticonCodeVoidRequest;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodeBatchResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodePageResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodeResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonCodeVoidResponse;
import org.firstfolio.gifticon.mapper.GifticonCodeMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GifticonCodeAdminService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 100;

    private final GifticonCodeMapper codeMapper;
    private final GifticonProductAdminService productService;
    private final GifticonCryptoService cryptoService;
    private final AdminAuditLogMapper auditLogMapper;
    private final Clock clock;
    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    public GifticonCodeAdminService(
            GifticonCodeMapper codeMapper,
            GifticonProductAdminService productService,
            GifticonCryptoService cryptoService,
            AdminAuditLogMapper auditLogMapper,
            Clock clock
    ) {
        this.codeMapper = codeMapper;
        this.productService = productService;
        this.cryptoService = cryptoService;
        this.auditLogMapper = auditLogMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminGifticonCodePageResponse findPage(
            long productId,
            String status,
            String expiresBefore,
            String cursor,
            Integer size
    ) {
        productService.requireProduct(productId);
        int pageSize = pageSize(size);
        List<GifticonCode> found = codeMapper.findPage(
                productId, parseStatusFilter(status), parseExpiresBefore(expiresBefore),
                parseCursor(cursor), pageSize + 1
        );
        boolean hasNext = found.size() > pageSize;
        List<GifticonCode> page = hasNext ? found.subList(0, pageSize) : found;
        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getGifticonCodeId()) : null;
        return new AdminGifticonCodePageResponse(
                page.stream().map(AdminGifticonCodeResponse::from).toList(), nextCursor
        );
    }

    @Transactional
    public AdminGifticonCodeBatchResponse createBatch(
            long productId,
            GifticonCodeBatchCreateRequest request,
            long actorUserId,
            String requestId
    ) {
        productService.requireProduct(productId);
        if (request == null || request.items() == null || request.items().isEmpty()
                || request.items().size() > MAX_BATCH_SIZE) {
            throw invalid("items는 1개 이상 100개 이하여야 합니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<GifticonCode> prepared = prepareCodes(productId, request.items(), now);
        try {
            for (GifticonCode code : prepared) codeMapper.insert(code);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(ErrorCode.GIFTICON_CODE_DUPLICATE);
        }
        auditLogMapper.insert(
                actorUserId, "STOCK_IN", "GIFTICON_CODE_BATCH", productId,
                null, batchSnapshot(productId, prepared), requestId, now
        );
        return new AdminGifticonCodeBatchResponse(
                prepared.size(), prepared.stream().map(AdminGifticonCodeResponse::from).toList()
        );
    }

    @Transactional
    public AdminGifticonCodeVoidResponse voidCode(
            long codeId,
            GifticonCodeVoidRequest request,
            long actorUserId,
            String requestId
    ) {
        String reason = requireReason(request);
        GifticonCode code = requireCode(codeId);
        assertVoidable(code);
        if (codeMapper.markVoidIfAvailable(codeId) != 1) {
            assertVoidable(requireCode(codeId));
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> before = codeSnapshot(code);
        code.setStatus("VOID");
        Map<String, Object> after = codeSnapshot(code);
        after.put("reason", reason);
        auditLogMapper.insert(
                actorUserId, "VOID", "GIFTICON_CODE", codeId,
                json(before), json(after), requestId, now
        );
        return new AdminGifticonCodeVoidResponse(codeId, "VOID");
    }

    private List<GifticonCode> prepareCodes(
            long productId,
            List<GifticonCodeCreateItemRequest> items,
            LocalDateTime now
    ) {
        List<GifticonCode> prepared = new ArrayList<>();
        Set<String> fingerprints = new LinkedHashSet<>();
        for (GifticonCodeCreateItemRequest item : items) {
            if (item == null || item.code() == null || item.code().isBlank()) {
                throw invalid("각 items.code는 필수입니다.");
            }
            String plaintext = item.code().trim();
            if (plaintext.length() > 500 || GifticonCryptoService.normalize(plaintext).isBlank()) {
                throw invalid("code가 너무 길거나 사용할 수 없는 형식입니다.");
            }
            if (item.expiresAt() == null || !item.expiresAt().isAfter(now)) {
                throw invalid("expires_at은 현재보다 이후여야 합니다.");
            }
            byte[] fingerprint = cryptoService.fingerprint(plaintext);
            if (!fingerprints.add(Base64.getEncoder().encodeToString(fingerprint))) {
                throw new ApiException(ErrorCode.GIFTICON_CODE_DUPLICATE, "요청 안에 중복 코드가 있습니다.");
            }
            GifticonCode code = new GifticonCode();
            code.setGifticonProductId(productId);
            code.setCodeCiphertext(cryptoService.encrypt(plaintext));
            code.setCodeMasked(cryptoService.mask(plaintext));
            code.setCodeFingerprint(fingerprint);
            code.setEncryptionKeyVersion(cryptoService.keyVersion());
            code.setExpiresAt(item.expiresAt());
            code.setStatus("AVAILABLE");
            code.setCreatedAt(now);
            prepared.add(code);
        }
        return prepared;
    }

    private GifticonCode requireCode(long codeId) {
        GifticonCode code = codeMapper.findById(codeId);
        if (code == null) throw new ApiException(ErrorCode.GIFTICON_CODE_NOT_FOUND);
        return code;
    }

    private static void assertVoidable(GifticonCode code) {
        if ("ASSIGNED".equals(code.getStatus())) {
            throw new ApiException(ErrorCode.GIFTICON_CODE_ALREADY_ASSIGNED);
        }
        if ("VOID".equals(code.getStatus())) {
            throw new ApiException(ErrorCode.GIFTICON_CODE_ALREADY_VOID);
        }
    }

    private String batchSnapshot(long productId, List<GifticonCode> codes) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("gifticon_product_id", productId);
        values.put("created_count", codes.size());
        values.put("items", codes.stream().map(this::codeSnapshot).toList());
        return json(values);
    }

    private Map<String, Object> codeSnapshot(GifticonCode code) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("gifticon_code_id", code.getGifticonCodeId());
        values.put("gifticon_product_id", code.getGifticonProductId());
        values.put("code_masked", code.getCodeMasked());
        values.put("status", code.getStatus());
        values.put("expires_at", code.getExpiresAt());
        return values;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "감사 이력을 만들 수 없습니다.", exception);
        }
    }

    private static String requireReason(GifticonCodeVoidRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw invalid("reason은 필수입니다.");
        }
        String reason = request.reason().trim();
        if (reason.length() > 500) throw invalid("reason은 500자 이하여야 합니다.");
        return reason;
    }

    private static String parseStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase();
        if (!Set.of("AVAILABLE", "ASSIGNED", "VOID").contains(normalized)) {
            throw new ApiException(ErrorCode.INVALID_GIFTICON_CODE_FILTER);
        }
        return normalized;
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            long value = Long.parseLong(cursor.trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.INVALID_GIFTICON_CODE_FILTER);
        }
    }

    private static LocalDateTime parseExpiresBefore(String expiresBefore) {
        if (expiresBefore == null || expiresBefore.isBlank()) return null;
        String value = expiresBefore.trim();
        try {
            return OffsetDateTime.parse(value)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException exception) {
                throw new ApiException(ErrorCode.INVALID_GIFTICON_CODE_FILTER);
            }
        }
    }

    private static int pageSize(Integer size) {
        if (size == null) return DEFAULT_PAGE_SIZE;
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.INVALID_GIFTICON_CODE_FILTER);
        }
        return size;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.GIFTICON_CODE_INVALID, message);
    }
}
