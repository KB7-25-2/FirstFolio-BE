package org.firstfolio.quiz.integration.ai.service;

import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionBatchItemRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionBatchRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionRequest;
import org.firstfolio.quiz.integration.ai.dto.response.QuizQuestionBatchItemResponse;
import org.firstfolio.quiz.integration.ai.dto.response.QuizQuestionBatchResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuizQuestionBatchService {

    private final QuizQuestionBatchStructureValidator structureValidator;
    private final QuizQuestionPayloadValidator payloadValidator;
    private final QuizChapterScopeValidator chapterScopeValidator;
    private final QuizQuestionBatchWriter writer;

    public QuizQuestionBatchService(
            QuizQuestionBatchStructureValidator structureValidator,
            QuizQuestionPayloadValidator payloadValidator,
            QuizChapterScopeValidator chapterScopeValidator,
            QuizQuestionBatchWriter writer
    ) {
        this.structureValidator = structureValidator;
        this.payloadValidator = payloadValidator;
        this.chapterScopeValidator = chapterScopeValidator;
        this.writer = writer;
    }

    public QuizQuestionBatchResponse process(QuizQuestionBatchRequest request) {
        structureValidator.validate(request);

        List<QuizQuestionBatchItemRequest> items = request.items();
        QuizQuestionBatchItemResponse[] results = new QuizQuestionBatchItemResponse[items.size()];

        List<Integer> pendingIndexes = new ArrayList<>();
        List<QuizQuestionRequest> pendingQuizzes = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            QuizQuestionBatchItemRequest item = items.get(i);
            QuizItemValidationResult validation = validateItem(item.quiz());
            if (validation.isValid()) {
                pendingIndexes.add(i);
                pendingQuizzes.add(item.quiz());
            } else {
                results[i] = QuizQuestionBatchItemResponse.rejected(
                        item.itemId(), validation.errorCode(), validation.errorMessage()
                );
            }
        }

        List<QuizQuestion> saved = writer.saveAll(pendingQuizzes);
        for (int i = 0; i < pendingIndexes.size(); i++) {
            int originalIndex = pendingIndexes.get(i);
            String itemId = items.get(originalIndex).itemId();
            QuizQuestion question = saved.get(i);
            results[originalIndex] = QuizQuestionBatchItemResponse.accepted(
                    itemId, question.getQuestionId(), question.getStatus()
            );
        }

        return QuizQuestionBatchResponse.of(request.batchId(), List.of(results));
    }

    private QuizItemValidationResult validateItem(QuizQuestionRequest quiz) {
        QuizItemValidationResult payloadResult = payloadValidator.validate(quiz);
        if (!payloadResult.isValid()) {
            return payloadResult;
        }
        return chapterScopeValidator.validate(quiz);
    }
}
