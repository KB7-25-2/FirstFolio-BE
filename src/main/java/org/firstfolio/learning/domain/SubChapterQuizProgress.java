package org.firstfolio.learning.domain;

public class SubChapterQuizProgress {

    private boolean completed;
    private Long activeAttemptId;
    private int answeredCount;
    private int totalCount;

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Long getActiveAttemptId() {
        return activeAttemptId;
    }

    public void setActiveAttemptId(Long activeAttemptId) {
        this.activeAttemptId = activeAttemptId;
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public void setAnsweredCount(int answeredCount) {
        this.answeredCount = answeredCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
