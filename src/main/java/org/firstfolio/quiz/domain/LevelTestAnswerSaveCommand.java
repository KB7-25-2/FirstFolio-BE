package org.firstfolio.quiz.domain;

public record LevelTestAnswerSaveCommand(
        Long questionId,
        String selectedKey
) {
}
