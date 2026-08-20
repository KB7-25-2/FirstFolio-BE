package org.firstfolio.quiz.integration.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizGenerationType;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionRequest;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        Map<Long, Integer> nextDisplayOrderByMainChapter = new HashMap<>();

        List<QuizQuestion> saved = new ArrayList<>();
        for (QuizQuestionRequest quiz : validQuizzes) {
            Integer displayOrder = resolveDisplayOrder(quiz, nextDisplayOrderByMainChapter);
            QuizQuestion question = toQuestion(quiz, aiCreatedBy, now, displayOrder);
            questionMapper.insert(question);
            saved.add(question);
        }
        return saved;
    }

    /**
     * MAIN_CHAPTER(대단원 시나리오) 문항만 display_order를 채운다. AI는 순서 개념이 없어
     * 대단원별 현재 최대값 다음 번호를 이어붙이고, 같은 배치 안에 같은 대단원 문항이 여럿이면
     * 배치 내에서 순번을 이어서 증가시킨다.
     */
    private Integer resolveDisplayOrder(
            QuizQuestionRequest quiz,
            Map<Long, Integer> nextDisplayOrderByMainChapter
    ) {
        if (quiz.usageType() != QuizUsageType.MAIN_CHAPTER) {
            return null;
        }

        long mainChapterId = quiz.mainChapterId();
        Integer next = nextDisplayOrderByMainChapter.get(mainChapterId);
        if (next == null) {
            Integer max = questionMapper.findMaxDisplayOrderByMainChapterId(mainChapterId);
            next = max == null ? 1 : max + 1;
        }
        nextDisplayOrderByMainChapter.put(mainChapterId, next + 1);
        return next;
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

    private QuizQuestion toQuestion(
            QuizQuestionRequest quiz,
            long aiCreatedBy,
            LocalDateTime now,
            Integer displayOrder
    ) {
        QuizQuestion question = QuizQuestion.draft(
                UUID.randomUUID().toString(),
                1,
                quiz.usageType(),
                quiz.mainChapterId(),
                quiz.subChapterId(),
                displayOrder,
                quiz.questionType(),
                quiz.difficulty(),
                quiz.prompt(),
                toJsonOrNull(quiz.scenarioJson()),
                toJson(quiz.optionsJson()),
                toJson(quiz.correctAnswerJson()),
                quiz.explanation(),
                QuizGenerationType.AI,
                quiz.questDate(),
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
