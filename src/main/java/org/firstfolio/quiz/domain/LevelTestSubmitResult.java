package org.firstfolio.quiz.domain;

import java.util.List;

public record LevelTestSubmitResult(
        long attemptId,
        QuizAttemptStatus status,
        List<LevelTestQuestionGradingResult> questionResults,
        List<LevelTestChapterGradingResult> chapterResults
) {
    public LevelTestSubmitResult {
        questionResults = List.copyOf(questionResults);
        chapterResults = List.copyOf(chapterResults);
    }
}
