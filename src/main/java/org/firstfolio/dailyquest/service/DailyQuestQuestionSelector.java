package org.firstfolio.dailyquest.service;

import org.firstfolio.dailyquest.domain.DailyQuestWrongAnswer;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.quiz.domain.QuizQuestion;
import org.firstfolio.quiz.domain.QuizQuestionStatus;
import org.firstfolio.quiz.domain.QuizUsageType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

final class DailyQuestQuestionSelector {

    static final int GENERAL_QUESTION_COUNT = 4;

    private static final Comparator<Weakness> WEAKNESS_ORDER = Comparator
            .comparing(Weakness::lastWrongAt)
            .reversed()
            .thenComparing(
                    Comparator.comparingInt(Weakness::wrongCount).reversed()
            )
            .thenComparingLong(Weakness::scopeId);

    List<QuizQuestion> select(
            List<QuizQuestion> candidates,
            List<DailyQuestWrongAnswer> wrongAnswers,
            Set<String> recentlyAssignedQuestionKeys
    ) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(wrongAnswers, "wrongAnswers must not be null");
        Objects.requireNonNull(
                recentlyAssignedQuestionKeys,
                "recentlyAssignedQuestionKeys must not be null"
        );

        List<QuizQuestion> orderedCandidates = candidates.stream()
                .peek(this::validateCandidate)
                .sorted(Comparator.comparingLong(
                        question -> question.getQuestionId()
                ))
                .toList();
        List<Weakness> weakSubChapters = aggregateWeaknesses(
                wrongAnswers,
                true
        );
        List<Weakness> weakMainChapters = aggregateWeaknesses(
                wrongAnswers,
                false
        );

        List<QuizQuestion> selected = new ArrayList<>();
        Set<String> selectedKeys = new HashSet<>();
        appendByPolicy(
                orderedCandidates,
                weakSubChapters,
                weakMainChapters,
                recentlyAssignedQuestionKeys,
                true,
                selected,
                selectedKeys
        );
        if (selected.size() < GENERAL_QUESTION_COUNT) {
            appendByPolicy(
                    orderedCandidates,
                    weakSubChapters,
                    weakMainChapters,
                    recentlyAssignedQuestionKeys,
                    false,
                    selected,
                    selectedKeys
            );
        }

        if (selected.size() != GENERAL_QUESTION_COUNT) {
            throw new ApiException(ErrorCode.DAILY_QUEST_POOL_UNAVAILABLE);
        }
        return List.copyOf(selected);
    }

    private void appendByPolicy(
            List<QuizQuestion> candidates,
            List<Weakness> weakSubChapters,
            List<Weakness> weakMainChapters,
            Set<String> recentKeys,
            boolean excludeRecent,
            List<QuizQuestion> selected,
            Set<String> selectedKeys
    ) {
        for (Weakness weakness : weakSubChapters) {
            appendMatching(
                    candidates,
                    question -> Objects.equals(
                            question.getSubChapterId(),
                            weakness.scopeId()
                    ),
                    recentKeys,
                    excludeRecent,
                    selected,
                    selectedKeys
            );
        }
        for (Weakness weakness : weakMainChapters) {
            appendMatching(
                    candidates,
                    question -> question.getMainChapterId()
                            == weakness.scopeId(),
                    recentKeys,
                    excludeRecent,
                    selected,
                    selectedKeys
            );
        }
        appendMatching(
                candidates,
                question -> true,
                recentKeys,
                excludeRecent,
                selected,
                selectedKeys
        );
    }

    private void appendMatching(
            List<QuizQuestion> candidates,
            Predicate<QuizQuestion> scope,
            Set<String> recentKeys,
            boolean excludeRecent,
            List<QuizQuestion> selected,
            Set<String> selectedKeys
    ) {
        if (selected.size() >= GENERAL_QUESTION_COUNT) {
            return;
        }
        for (QuizQuestion candidate : candidates) {
            if (selected.size() >= GENERAL_QUESTION_COUNT) {
                return;
            }
            String questionKey = candidate.getQuestionKey();
            if (!scope.test(candidate)
                    || selectedKeys.contains(questionKey)
                    || (excludeRecent && recentKeys.contains(questionKey))) {
                continue;
            }
            selected.add(candidate);
            selectedKeys.add(questionKey);
        }
    }

    private List<Weakness> aggregateWeaknesses(
            List<DailyQuestWrongAnswer> wrongAnswers,
            boolean subChapter
    ) {
        Map<Long, WeaknessAccumulator> byScope = new HashMap<>();
        for (DailyQuestWrongAnswer wrongAnswer : wrongAnswers) {
            validateWrongAnswer(wrongAnswer);
            Long scopeId;
            if (subChapter) {
                scopeId = wrongAnswer.getSubChapterId();
            } else {
                scopeId = wrongAnswer.getMainChapterId();
            }
            if (scopeId == null) {
                continue;
            }
            byScope.computeIfAbsent(
                    scopeId,
                    ignored -> new WeaknessAccumulator(scopeId)
            ).add(
                    wrongAnswer.getAnsweredAt(),
                    wrongAnswer.getWrongCount()
            );
        }
        return byScope.values().stream()
                .map(WeaknessAccumulator::toWeakness)
                .sorted(WEAKNESS_ORDER)
                .toList();
    }

    private void validateCandidate(QuizQuestion candidate) {
        if (candidate == null
                || candidate.getQuestionId() == null
                || candidate.getQuestionId() <= 0
                || candidate.getQuestionKey() == null
                || candidate.getQuestionKey().isBlank()
                || candidate.getVersionNo() <= 0
                || candidate.getUsageType() != QuizUsageType.DAILY_GENERAL
                || candidate.getStatus() != QuizQuestionStatus.PUBLISHED
                || candidate.getMainChapterId() == null
                || candidate.getMainChapterId() <= 0
                || (candidate.getSubChapterId() != null
                    && candidate.getSubChapterId() <= 0)) {
            throw new ApiException(ErrorCode.DAILY_QUEST_POOL_UNAVAILABLE);
        }
    }

    private void validateWrongAnswer(DailyQuestWrongAnswer wrongAnswer) {
        if (wrongAnswer == null
                || wrongAnswer.getQuestionKey() == null
                || wrongAnswer.getQuestionKey().isBlank()
                || wrongAnswer.getMainChapterId() <= 0
                || (wrongAnswer.getSubChapterId() != null
                    && wrongAnswer.getSubChapterId() <= 0)
                || wrongAnswer.getAnsweredAt() == null
                || wrongAnswer.getWrongCount() <= 0) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private record Weakness(
            long scopeId,
            LocalDateTime lastWrongAt,
            int wrongCount
    ) {
    }

    private static final class WeaknessAccumulator {

        private final long scopeId;
        private LocalDateTime lastWrongAt;
        private int wrongCount;

        private WeaknessAccumulator(long scopeId) {
            this.scopeId = scopeId;
        }

        private void add(LocalDateTime answeredAt, int count) {
            if (lastWrongAt == null || answeredAt.isAfter(lastWrongAt)) {
                lastWrongAt = answeredAt;
            }
            wrongCount += count;
        }

        private Weakness toWeakness() {
            return new Weakness(scopeId, lastWrongAt, wrongCount);
        }
    }
}
