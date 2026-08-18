package org.firstfolio.quiz.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.dto.response.QuizQuestionListItemResponse;
import org.firstfolio.quiz.dto.response.QuizQuestionPageResponse;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuizQuestionQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final QuizQuestionMapper quizQuestionMapper;

    public QuizQuestionQueryService(QuizQuestionMapper quizQuestionMapper) {
        this.quizQuestionMapper = quizQuestionMapper;
    }

    @Transactional(readOnly = true)
    public QuizQuestionPageResponse findPage(
            String usageType,
            Long mainChapterId,
            Long subChapterId,
            String status,
            String questionKey,
            String cursor
    ) {
        List<QuizQuestion> found = quizQuestionMapper.findPage(
                parseUsageType(usageType),
                mainChapterId,
                subChapterId,
                parseStatus(status),
                normalizeQuestionKey(questionKey),
                parseCursor(cursor),
                DEFAULT_PAGE_SIZE + 1
        );

        boolean hasNext = found.size() > DEFAULT_PAGE_SIZE;
        List<QuizQuestion> page = hasNext
                ? found.subList(0, DEFAULT_PAGE_SIZE)
                : found;

        List<QuizQuestionListItemResponse> items = new ArrayList<>(page.size());
        for (QuizQuestion question : page) {
            items.add(QuizQuestionListItemResponse.from(question));
        }

        String nextCursor = hasNext
                ? String.valueOf(page.get(page.size() - 1).getQuestionId())
                : null;

        return new QuizQuestionPageResponse(items, nextCursor);
    }

    private static QuizUsageType parseUsageType(String usageType) {
        if (usageType == null || usageType.isBlank()) {
            return null;
        }

        try {
            return QuizUsageType.valueOf(usageType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "usage_type 값이 올바르지 않습니다."
            );
        }
    }

    private static QuizQuestionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return QuizQuestionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "status 값이 올바르지 않습니다."
            );
        }
    }

    private static String normalizeQuestionKey(String questionKey) {
        if (questionKey == null || questionKey.isBlank()) {
            return null;
        }
        return questionKey.trim();
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "cursor 값이 올바르지 않습니다."
            );
        }
    }
}
