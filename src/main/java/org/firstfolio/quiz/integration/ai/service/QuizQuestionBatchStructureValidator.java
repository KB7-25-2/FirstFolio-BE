package org.firstfolio.quiz.integration.ai.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionBatchItemRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionBatchRequest;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class QuizQuestionBatchStructureValidator {

    private static final int MAX_ITEMS = 100;

    public void validate(QuizQuestionBatchRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.INVALID_BATCH_REQUEST, "요청 본문이 비어 있습니다.");
        }
        requireUuid(request.batchId(), "batch_id");

        List<QuizQuestionBatchItemRequest> items = request.items();
        if (items == null || items.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_BATCH_REQUEST, "items는 1건 이상이어야 합니다.");
        }
        if (items.size() > MAX_ITEMS) {
            throw new ApiException(ErrorCode.BATCH_SIZE_EXCEEDED);
        }

        Set<String> itemIds = new HashSet<>();
        for (QuizQuestionBatchItemRequest item : items) {
            if (item == null) {
                throw new ApiException(ErrorCode.INVALID_BATCH_REQUEST, "items 항목이 비어 있습니다.");
            }
            requireUuid(item.itemId(), "item_id");
            if (!itemIds.add(item.itemId())) {
                throw new ApiException(
                        ErrorCode.INVALID_BATCH_REQUEST,
                        "item_id는 한 요청 내에서 중복될 수 없습니다: " + item.itemId()
                );
            }
        }
    }

    private void requireUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_BATCH_REQUEST, fieldName + "는 필수입니다.");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    ErrorCode.INVALID_BATCH_REQUEST,
                    fieldName + "는 UUID 형식이어야 합니다: " + value
            );
        }
    }
}
