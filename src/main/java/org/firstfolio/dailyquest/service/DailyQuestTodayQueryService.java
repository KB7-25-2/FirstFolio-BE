package org.firstfolio.dailyquest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.dailyquest.domain.DailyQuestAssignmentResult;
import org.firstfolio.dailyquest.domain.DailyQuestItem;
import org.firstfolio.dailyquest.domain.DailyQuestQuestionView;
import org.firstfolio.dailyquest.domain.DailyQuestTodayResult;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizAttemptQuestion;
import org.firstfolio.quiz.service.QuizQuestionSnapshotCodec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DailyQuestTodayQueryService {

    private final DailyQuestAssignmentService assignmentService;
    private final QuizQuestionSnapshotCodec snapshotCodec;
    private final ObjectMapper objectMapper;

    public DailyQuestTodayQueryService(
            DailyQuestAssignmentService assignmentService
    ) {
        this.assignmentService = assignmentService;
        this.snapshotCodec = new QuizQuestionSnapshotCodec();
        this.objectMapper = new ObjectMapper();
    }

    public DailyQuestTodayResult getToday(long userId) {
        DailyQuestAssignmentResult assignment = assignmentService
                .assignToday(userId);
        List<DailyQuestQuestionView> questions = new ArrayList<>();
        int answeredCount = 0;

        for (DailyQuestItem item : assignment.items()) {
            QuizAttemptQuestion question = snapshotCodec.toQuestionView(
                    item.getQuestionId(),
                    item.getDisplayOrder(),
                    item.getQuestionSnapshotJson()
            );
            String savedAnswerKey = parseSavedAnswerKey(
                    item.getUserAnswerJson(),
                    question
            );
            if (savedAnswerKey != null) {
                answeredCount++;
            }
            questions.add(new DailyQuestQuestionView(
                    item.getDailyQuestItemId(),
                    question.questionId(),
                    question.displayOrder(),
                    question.questionType(),
                    question.generationType(),
                    question.prompt(),
                    question.scenario(),
                    question.choices(),
                    savedAnswerKey
            ));
        }

        return new DailyQuestTodayResult(
                assignment.dailyQuest(),
                answeredCount,
                questions
        );
    }

    private String parseSavedAnswerKey(
            String userAnswerJson,
            QuizAttemptQuestion question
    ) {
        if (userAnswerJson == null) {
            return null;
        }
        try {
            JsonNode answer = objectMapper.readTree(userAnswerJson);
            if (answer == null || !answer.isObject()) {
                throw invalidStoredAnswer(null);
            }
            String key = answer.path("key").textValue();
            if (key == null
                    || key.isBlank()
                    || question.choices().stream().noneMatch(
                        choice -> choice.key().equals(key)
                    )) {
                throw invalidStoredAnswer(null);
            }
            return key;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalidStoredAnswer(exception);
        }
    }

    private ApiException invalidStoredAnswer(Throwable cause) {
        return new ApiException(
                ErrorCode.INTERNAL_ERROR,
                "저장된 일일 퀘스트 답안이 올바르지 않습니다.",
                cause
        );
    }
}
