package org.firstfolio.quiz.domain;

import java.time.LocalDateTime;

public class QuizAttempt {

    private Long attemptId;
    private long userId;
    private QuizType quizType;
    private Long mainChapterId;
    private Long subChapterId;
    private Long contentVersionId;
    private int attemptNo;
    private QuizAttemptStatus status;
    private int totalCount;
    private int correctCount;
    private int score;
    private Long rewardPolicyId;
    private Long pointTransactionId;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public QuizType getQuizType() {
        return quizType;
    }

    public void setQuizType(QuizType quizType) {
        this.quizType = quizType;
    }

    public Long getMainChapterId() {
        return mainChapterId;
    }

    public void setMainChapterId(Long mainChapterId) {
        this.mainChapterId = mainChapterId;
    }

    public Long getSubChapterId() {
        return subChapterId;
    }

    public void setSubChapterId(Long subChapterId) {
        this.subChapterId = subChapterId;
    }

    public Long getContentVersionId() {
        return contentVersionId;
    }

    public void setContentVersionId(Long contentVersionId) {
        this.contentVersionId = contentVersionId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(int attemptNo) {
        this.attemptNo = attemptNo;
    }

    public QuizAttemptStatus getStatus() {
        return status;
    }

    public void setStatus(QuizAttemptStatus status) {
        this.status = status;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Long getRewardPolicyId() {
        return rewardPolicyId;
    }

    public void setRewardPolicyId(Long rewardPolicyId) {
        this.rewardPolicyId = rewardPolicyId;
    }

    public Long getPointTransactionId() {
        return pointTransactionId;
    }

    public void setPointTransactionId(Long pointTransactionId) {
        this.pointTransactionId = pointTransactionId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}
