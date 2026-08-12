package org.firstfolio.quiz.domain;

import org.firstfolio.curriculum.domain.MainChapter;

import java.util.List;

public record LevelTestQuestionSet(
        List<MainChapter> mainChapters,
        List<QuizQuestion> questions
) {
    public LevelTestQuestionSet {
        mainChapters = List.copyOf(mainChapters);
        questions = List.copyOf(questions);
    }
}
