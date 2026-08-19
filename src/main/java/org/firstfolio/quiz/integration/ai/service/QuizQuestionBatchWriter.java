package org.firstfolio.quiz.integration.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionRequest;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI 배치 전용 시스템 사용자 ID({@code quiz.batch.ai-created-by})는 관리자 계정이 정해지기 전까지
 * 비어 있을 수 있다. 생성자 주입 대신 호출 시점에 읽어서, 값이 없어도 서버 기동 자체는 막지 않는다.
 */
@Component
public class QuizQuestionBatchWriter {

    private static final String AI_CREATED_BY_PROPERTY = "quiz.batch.ai-created-by";

    private final QuizQuestionMapper questionMapper;
    private final Environment environment;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public QuizQuestionBatchWriter(
            QuizQuestionMapper questionMapper,
            Environment environment,
            Clock clock
    ) {
        this.questionMapper = questionMapper;
        this.environment = environment;
        this.clock = clock;
        this.objectMapper = ApiObjectMapperFactory.create();
    }

    @Transactional
    public List<QuizQuestion> saveAll(List<QuizQuestionRequest> validQuizzes) {
        if (validQuizzes.isEmpty()) {
            return List.of();
        }

        long aiCreatedBy = resolveAiCreatedBy();
        LocalDateTime now = LocalDateTime.now(clock);

        List<QuizQuestion> saved = new ArrayList<>();
        for (QuizQuestionRequest quiz : validQuizzes) {
            QuizQuestion question = toQuestion(quiz, aiCreatedBy, now);
            questionMapper.insert(question);
            saved.add(question);
        }
        return saved;
    }

    private long resolveAiCreatedBy() {
        String value = environment.getProperty(AI_CREATED_BY_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    AI_CREATED_BY_PROPERTY + " 설정이 없어 AI 배치 문항을 저장할 수 없습니다."
            );
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    AI_CREATED_BY_PROPERTY + " 값이 올바른 사용자 ID가 아닙니다: " + value
            );
        }
    }

    private QuizQuestion toQuestion(QuizQuestionRequest quiz, long aiCreatedBy, LocalDateTime now) {
        QuizQuestion question = QuizQuestion.draft(
                UUID.randomUUID().toString(),
                1,
                quiz.usageType(),
                quiz.mainChapterId(),
                quiz.subChapterId(),
                null,
                quiz.questionType(),
                quiz.difficulty(),
                quiz.prompt(),
                toJsonOrNull(quiz.scenarioJson()),
                toJson(quiz.optionsJson()),
                toJson(quiz.correctAnswerJson()),
                quiz.explanation(),
                QuizGenerationType.AI,
                null,
                null,
                aiCreatedBy,
                now
        );
        question.setStatus(QuizQuestionStatus.REVIEW);
        return question;
    }

    private String toJsonOrNull(Object value) {
        return value == null ? null : toJson(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("퀴즈 문항 JSON을 직렬화할 수 없습니다.", exception);
        }
    }
}
