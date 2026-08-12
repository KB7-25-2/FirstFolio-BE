package org.firstfolio.quiz.domain;

import java.util.List;

public record LevelTestAttemptStartResult(
        QuizAttempt attempt,
        List<LevelTestAttemptQuestion> questions,
        List<LevelTestSavedAnswer> answers
) {
    public LevelTestAttemptStartResult {
        questions = List.copyOf(questions);
        answers = List.copyOf(answers);
    }
}
