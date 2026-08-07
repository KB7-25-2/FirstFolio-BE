package org.firstfolio.content.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.firstfolio.quiz.domain.QuizQuestionReference;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.mapper.QuizQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 강좌 JSON의 구조와 DB 참조를 배포 전에 함께 검증한다.
 */
@Service
public class LessonContentValidationService {

    private static final String QUESTION_IDS_PATH = "/subChapterQuiz/questionIds/";

    private final LessonSchemaValidator schemaValidator;
    private final SubChapterMapper subChapterMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final ObjectMapper objectMapper;

    public LessonContentValidationService(
            LessonSchemaValidator schemaValidator,
            SubChapterMapper subChapterMapper,
            QuizQuestionMapper quizQuestionMapper
    ) {
        this.schemaValidator = schemaValidator;
        this.subChapterMapper = subChapterMapper;
        this.quizQuestionMapper = quizQuestionMapper;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public LessonValidationResult validate(long subChapterId, byte[] content) {
        LessonValidationResult schemaResult = schemaValidator.validate(content);
        if (!schemaResult.isValid()) {
            return schemaResult;
        }

        if (subChapterMapper.findById(subChapterId) == null) {
            return LessonValidationResult.invalid(new LessonValidationError(
                    LessonValidationErrorCode.SUB_CHAPTER_NOT_FOUND,
                    "/subChapterId",
                    "업로드 대상 소단원을 찾을 수 없습니다: " + subChapterId
            ));
        }

        List<Long> questionIds = extractQuestionIds(content);
        List<QuizQuestionReference> references =
                quizQuestionMapper.findReferencesByIds(questionIds);
        Map<Long, QuizQuestionReference> referenceById = indexByQuestionId(references);
        List<LessonValidationError> errors = new ArrayList<>();

        for (int index = 0; index < questionIds.size(); index++) {
            long questionId = questionIds.get(index);
            QuizQuestionReference reference = referenceById.get(questionId);
            String path = QUESTION_IDS_PATH + index;

            if (reference == null) {
                errors.add(new LessonValidationError(
                        LessonValidationErrorCode.REFERENCED_QUESTION_NOT_FOUND,
                        path,
                        "참조한 퀴즈 문항을 찾을 수 없습니다: " + questionId
                ));
                continue;
            }

            if (reference.getStatus() != QuizQuestionStatus.PUBLISHED) {
                errors.add(new LessonValidationError(
                        LessonValidationErrorCode.QUESTION_NOT_PUBLISHED,
                        path,
                        "게시 상태가 아닌 퀴즈 문항입니다: " + questionId
                ));
            }
            if (reference.getUsageType() != QuizUsageType.SUB_CHAPTER) {
                errors.add(new LessonValidationError(
                        LessonValidationErrorCode.QUESTION_USAGE_TYPE_MISMATCH,
                        path,
                        "소단원 퀴즈 문항이 아닙니다: " + questionId
                ));
            }
            if (!Objects.equals(reference.getSubChapterId(), subChapterId)) {
                errors.add(new LessonValidationError(
                        LessonValidationErrorCode.QUESTION_SUB_CHAPTER_MISMATCH,
                        path,
                        "업로드 대상 소단원에 속하지 않은 퀴즈 문항입니다: " + questionId
                ));
            }
        }

        return errors.isEmpty()
                ? LessonValidationResult.valid()
                : new LessonValidationResult(errors);
    }

    private List<Long> extractQuestionIds(byte[] content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            List<Long> questionIds = new ArrayList<>();
            for (JsonNode questionId : root.path("subChapterQuiz").path("questionIds")) {
                questionIds.add(questionId.longValue());
            }
            return List.copyOf(questionIds);
        } catch (IOException exception) {
            throw new IllegalStateException("검증을 통과한 강좌 JSON을 다시 읽지 못했습니다.", exception);
        }
    }

    private Map<Long, QuizQuestionReference> indexByQuestionId(
            List<QuizQuestionReference> references
    ) {
        Map<Long, QuizQuestionReference> referenceById = new HashMap<>();
        for (QuizQuestionReference reference : references) {
            referenceById.put(reference.getQuestionId(), reference);
        }
        return referenceById;
    }
}
