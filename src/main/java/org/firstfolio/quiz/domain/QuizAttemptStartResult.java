package org.firstfolio.quiz.domain;

import java.util.List;
import java.util.Objects;

public record QuizAttemptStartResult(
        QuizAttempt attempt,
        List<QuizAttemptQuestion> questions
) {
    public QuizAttemptStartResult {
        Objects.requireNonNull(attempt, "attempt must not be null");
        questions = List.copyOf(questions);
    }
}
