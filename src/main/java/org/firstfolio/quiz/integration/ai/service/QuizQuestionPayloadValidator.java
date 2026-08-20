package org.firstfolio.quiz.integration.ai.service;

import org.firstfolio.quiz.domain.QuizQuestionType;
import org.firstfolio.quiz.domain.QuizUsageType;
import org.firstfolio.quiz.integration.ai.dto.request.QuizOptionRequest;
import org.firstfolio.quiz.integration.ai.dto.request.QuizQuestionRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class QuizQuestionPayloadValidator {

    private static final Set<String> TRUE_FALSE_KEYS = Set.of("O", "X");
    private static final Set<String> FOUR_CHOICE_KEYS = Set.of("1", "2", "3", "4");

    public QuizItemValidationResult validate(QuizQuestionRequest quiz) {
        if (quiz == null) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_QUIZ_PAYLOAD, "quiz는 필수입니다."
            );
        }

        QuizItemValidationResult typeResult = validateUsageAndQuestionType(quiz);
        if (!typeResult.isValid()) {
            return typeResult;
        }

        if (quiz.difficulty() == null) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_DIFFICULTY, "difficulty는 필수입니다."
            );
        }
        if (!StringUtils.hasText(quiz.prompt()) || !StringUtils.hasText(quiz.explanation())) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_QUIZ_PAYLOAD, "prompt와 explanation은 비어 있을 수 없습니다."
            );
        }

        QuizItemValidationResult chapterScopeResult = validateChapterScope(quiz);
        if (!chapterScopeResult.isValid()) {
            return chapterScopeResult;
        }

        QuizItemValidationResult scenarioResult = validateScenarioPresence(quiz);
        if (!scenarioResult.isValid()) {
            return scenarioResult;
        }

        QuizItemValidationResult questDateResult = validateQuestDate(quiz);
        if (!questDateResult.isValid()) {
            return questDateResult;
        }

        QuizItemValidationResult optionsResult = validateOptions(quiz);
        if (!optionsResult.isValid()) {
            return optionsResult;
        }

        return validateCorrectAnswer(quiz);
    }

    private QuizItemValidationResult validateUsageAndQuestionType(QuizQuestionRequest quiz) {
        QuizQuestionType questionType = quiz.questionType();
        if (questionType != QuizQuestionType.TRUE_FALSE
                && questionType != QuizQuestionType.SINGLE_CHOICE
                && questionType != QuizQuestionType.SCENARIO) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_QUESTION_TYPE,
                    "question_type은 TRUE_FALSE, SINGLE_CHOICE, SCENARIO만 허용합니다."
            );
        }

        boolean usageTypeAllowed = questionType == QuizQuestionType.SCENARIO
                ? quiz.usageType() == QuizUsageType.MAIN_CHAPTER
                        || quiz.usageType() == QuizUsageType.DAILY_NEWS
                : quiz.usageType() == QuizUsageType.SUB_CHAPTER
                        || quiz.usageType() == QuizUsageType.DAILY_GENERAL;

        if (!usageTypeAllowed) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_USAGE_TYPE,
                    "usage_type(" + quiz.usageType() + ")이 question_type(" + questionType + ")과 맞지 않습니다."
            );
        }

        return QuizItemValidationResult.valid();
    }

    private QuizItemValidationResult validateChapterScope(QuizQuestionRequest quiz) {
        if (quiz.usageType() == QuizUsageType.DAILY_NEWS) {
            if (quiz.mainChapterId() != null || quiz.subChapterId() != null) {
                return QuizItemValidationResult.invalid(
                        QuizItemErrorCode.INVALID_CHAPTER_SCOPE,
                        "DAILY_NEWS 문제는 main_chapter_id, sub_chapter_id가 모두 null이어야 합니다."
                );
            }
            return QuizItemValidationResult.valid();
        }

        if (quiz.mainChapterId() == null) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_CHAPTER_SCOPE, "main_chapter_id는 필수입니다."
            );
        }

        boolean subChapterRequired = quiz.usageType() == QuizUsageType.SUB_CHAPTER
                || quiz.usageType() == QuizUsageType.DAILY_GENERAL;
        boolean subChapterPresent = quiz.subChapterId() != null;

        if (subChapterRequired != subChapterPresent) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_CHAPTER_SCOPE,
                    "sub_chapter_id는 SUB_CHAPTER, DAILY_GENERAL 문제만 필수이고 MAIN_CHAPTER 문제는 null이어야 합니다."
            );
        }

        return QuizItemValidationResult.valid();
    }

    private QuizItemValidationResult validateQuestDate(QuizQuestionRequest quiz) {
        boolean questDateRequired = quiz.usageType() == QuizUsageType.DAILY_NEWS;
        boolean questDatePresent = quiz.questDate() != null;

        if (questDateRequired != questDatePresent) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_QUEST_DATE,
                    "quest_date는 DAILY_NEWS 문제만 필수이고 나머지 유형은 null이어야 합니다."
            );
        }

        return QuizItemValidationResult.valid();
    }

    private QuizItemValidationResult validateScenarioPresence(QuizQuestionRequest quiz) {
        boolean scenarioRequired = quiz.questionType() == QuizQuestionType.SCENARIO;
        boolean scenarioPresent = quiz.scenarioJson() != null;

        if (scenarioRequired != scenarioPresent) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_SCENARIO,
                    "scenario_json은 SCENARIO 문제만 필수이고 나머지 유형은 null이어야 합니다."
            );
        }

        return QuizItemValidationResult.valid();
    }

    private QuizItemValidationResult validateOptions(QuizQuestionRequest quiz) {
        List<QuizOptionRequest> options = quiz.optionsJson();
        if (options == null || options.isEmpty()) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_OPTIONS, "options_json은 비어 있을 수 없습니다."
            );
        }

        Set<String> expectedKeys = quiz.questionType() == QuizQuestionType.TRUE_FALSE
                ? TRUE_FALSE_KEYS
                : FOUR_CHOICE_KEYS;

        if (options.size() != expectedKeys.size()) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_OPTIONS,
                    "선택지 개수가 올바르지 않습니다. 기대값: " + expectedKeys.size()
            );
        }

        Set<String> actualKeys = options.stream()
                .map(QuizOptionRequest::key)
                .collect(Collectors.toSet());

        if (actualKeys.size() != options.size() || !actualKeys.equals(expectedKeys)) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_OPTIONS,
                    "선택지 키가 중복되었거나 허용된 키 집합과 다릅니다: " + expectedKeys
            );
        }

        return QuizItemValidationResult.valid();
    }

    private QuizItemValidationResult validateCorrectAnswer(QuizQuestionRequest quiz) {
        String correctKey = quiz.correctAnswerJson() == null ? null : quiz.correctAnswerJson().key();
        if (!StringUtils.hasText(correctKey)) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_CORRECT_ANSWER, "correct_answer_json.key는 필수입니다."
            );
        }

        boolean existsInOptions = quiz.optionsJson().stream()
                .anyMatch(option -> correctKey.equals(option.key()));

        if (!existsInOptions) {
            return QuizItemValidationResult.invalid(
                    QuizItemErrorCode.INVALID_CORRECT_ANSWER, "정답 키가 선택지에 없습니다: " + correctKey
            );
        }

        return QuizItemValidationResult.valid();
    }
}
