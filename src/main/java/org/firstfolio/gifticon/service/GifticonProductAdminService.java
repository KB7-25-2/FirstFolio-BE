package org.firstfolio.gifticon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.curriculum.mapper.AdminAuditLogMapper;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.gifticon.domain.GifticonProduct;
import org.firstfolio.gifticon.domain.GifticonProductInventory;
import org.firstfolio.gifticon.dto.request.GifticonProductCreateRequest;
import org.firstfolio.gifticon.dto.request.GifticonProductPatchRequest;
import org.firstfolio.gifticon.dto.response.AdminGifticonProductPageResponse;
import org.firstfolio.gifticon.dto.response.AdminGifticonProductResponse;
import org.firstfolio.gifticon.mapper.GifticonProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GifticonProductAdminService {

    public static final String ON_SALE = "ON_SALE";
    public static final String STOPPED = "STOPPED";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final GifticonProductMapper productMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final Clock clock;
    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    public GifticonProductAdminService(
            GifticonProductMapper productMapper,
            AdminAuditLogMapper auditLogMapper,
            Clock clock
    ) {
        this.productMapper = productMapper;
        this.auditLogMapper = auditLogMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminGifticonProductPageResponse findPage(String status, String cursor, Integer size) {
        int pageSize = pageSize(size);
        List<GifticonProductInventory> found = productMapper.findPage(
                parseStatusFilter(status), parseCursor(cursor), pageSize + 1
        );
        boolean hasNext = found.size() > pageSize;
        List<GifticonProductInventory> page = hasNext ? found.subList(0, pageSize) : found;
        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getGifticonProductId()) : null;
        return new AdminGifticonProductPageResponse(
                page.stream().map(AdminGifticonProductResponse::from).toList(),
                nextCursor
        );
    }

    @Transactional
    public AdminGifticonProductResponse create(
            GifticonProductCreateRequest request,
            long actorUserId,
            String requestId
    ) {
        if (request == null) {
            throw invalid("요청 본문은 필수입니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        GifticonProduct product = new GifticonProduct();
        product.setName(requiredText(request.name(), "name", 100));
        product.setBrandName(requiredText(request.brandName(), "brand_name", 100));
        product.setCategory(requiredText(request.category(), "category", 50));
        int faceValue = positiveFaceValue(request.faceValueKrw());
        product.setFaceValueKrw(faceValue);
        product.setRequiredPoints(faceValue);
        product.setStatus(request.status() == null ? STOPPED : requireStatus(request.status()));
        product.setImageUrl(optionalText(request.imageUrl(), "image_url", 1000));
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productMapper.insert(product);
        auditLogMapper.insert(
                actorUserId, "CREATE", "GIFTICON_PRODUCT", product.getGifticonProductId(),
                null, snapshot(product), requestId, now
        );
        return AdminGifticonProductResponse.from(product);
    }

    @Transactional
    public AdminGifticonProductResponse patch(
            long productId,
            GifticonProductPatchRequest request,
            long actorUserId,
            String requestId
    ) {
        GifticonProduct product = requireProduct(productId);
        if (request == null || allFieldsMissing(request)) {
            throw invalid("수정할 필드를 하나 이상 입력해야 합니다.");
        }
        String before = snapshot(product);
        if (request.name() != null) product.setName(requiredText(request.name(), "name", 100));
        if (request.brandName() != null) product.setBrandName(requiredText(request.brandName(), "brand_name", 100));
        if (request.category() != null) product.setCategory(requiredText(request.category(), "category", 50));
        if (request.faceValueKrw() != null) {
            int faceValue = positiveFaceValue(request.faceValueKrw());
            product.setFaceValueKrw(faceValue);
            product.setRequiredPoints(faceValue);
        }
        if (request.status() != null) product.setStatus(requireStatus(request.status()));
        if (request.imageUrl() != null) product.setImageUrl(optionalText(request.imageUrl(), "image_url", 1000));
        LocalDateTime now = LocalDateTime.now(clock);
        product.setUpdatedAt(now);
        productMapper.update(product);
        auditLogMapper.insert(
                actorUserId, "UPDATE", "GIFTICON_PRODUCT", productId,
                before, snapshot(product), requestId, now
        );
        return AdminGifticonProductResponse.from(product);
    }

    public GifticonProduct requireProduct(long productId) {
        GifticonProduct product = productMapper.findById(productId);
        if (product == null) throw new ApiException(ErrorCode.GIFTICON_PRODUCT_NOT_FOUND);
        return product;
    }

    private String snapshot(GifticonProduct product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("gifticon_product_id", product.getGifticonProductId());
        values.put("name", product.getName());
        values.put("brand_name", product.getBrandName());
        values.put("category", product.getCategory());
        values.put("face_value_krw", product.getFaceValueKrw());
        values.put("required_points", product.getRequiredPoints());
        values.put("status", product.getStatus());
        values.put("image_url", product.getImageUrl());
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "감사 이력을 만들 수 없습니다.", exception);
        }
    }

    private static boolean allFieldsMissing(GifticonProductPatchRequest request) {
        return request.name() == null && request.brandName() == null && request.category() == null
                && request.faceValueKrw() == null && request.imageUrl() == null && request.status() == null;
    }

    private static int positiveFaceValue(Integer value) {
        if (value == null || value <= 0) throw invalid("face_value_krw는 1 이상이어야 합니다.");
        return value;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw invalid(field + "는 필수입니다.");
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw invalid(field + "가 너무 깁니다.");
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw invalid(field + "가 너무 깁니다.");
        return normalized;
    }

    private static String requireStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!ON_SALE.equals(normalized) && !STOPPED.equals(normalized)) {
            throw invalid("status는 ON_SALE 또는 STOPPED만 허용합니다.");
        }
        return normalized;
    }

    private static String parseStatusFilter(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return requireStatus(value);
        } catch (ApiException exception) {
            throw new ApiException(ErrorCode.INVALID_GIFTICON_FILTER);
        }
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
        if (size <= 0 || size > MAX_PAGE_SIZE) throw new ApiException(ErrorCode.INVALID_GIFTICON_FILTER);
        return size;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.GIFTICON_PRODUCT_INVALID, message);
    }
}
