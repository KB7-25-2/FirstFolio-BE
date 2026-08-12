package org.firstfolio.quiz.domain;

import java.util.List;

public record LevelTestAttemptAggregate(
        QuizAttempt attempt,
        List<QuizAnswer> answers
) {
    public LevelTestAttemptAggregate {
        answers = List.copyOf(answers);
    }
}
