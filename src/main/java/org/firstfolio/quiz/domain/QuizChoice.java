package org.firstfolio.quiz.domain;

import java.util.Objects;

public record QuizChoice(String key, String label) {

    public QuizChoice {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(label, "label must not be null");
    }
}
