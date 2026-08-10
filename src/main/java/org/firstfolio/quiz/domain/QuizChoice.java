package org.firstfolio.quiz.domain;

import java.util.Objects;

public record QuizChoice(String id, String text) {

    public QuizChoice {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
