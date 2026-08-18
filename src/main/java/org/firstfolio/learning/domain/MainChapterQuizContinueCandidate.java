package org.firstfolio.learning.domain;

import org.firstfolio.quiz.domain.QuizAttemptStatus;

public class MainChapterQuizContinueCandidate {

    private long curriculumItemId;
    private long mainChapterId;
    private Long attemptId;
    private QuizAttemptStatus attemptStatus;

    public long getCurriculumItemId() {
        return curriculumItemId;
    }

    public void setCurriculumItemId(long curriculumItemId) {
        this.curriculumItemId = curriculumItemId;
    }

    public long getMainChapterId() {
        return mainChapterId;
    }

    public void setMainChapterId(long mainChapterId) {
        this.mainChapterId = mainChapterId;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public QuizAttemptStatus getAttemptStatus() {
        return attemptStatus;
    }

    public void setAttemptStatus(QuizAttemptStatus attemptStatus) {
        this.attemptStatus = attemptStatus;
    }
}
