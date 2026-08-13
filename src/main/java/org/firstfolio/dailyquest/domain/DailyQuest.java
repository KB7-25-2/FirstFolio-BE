package org.firstfolio.dailyquest.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public class DailyQuest {

    public static final int TOTAL_QUESTION_COUNT = 5;

    private Long dailyQuestId;
    private long userId;
    private LocalDate questDate;
    private DailyQuestStatus status;
    private int totalCount;
    private int correctCount;
    private int score;
    private Long rewardPolicyId;
    private Long pointTransactionId;
    private LocalDateTime completedAt;

    public DailyQuest() {
    }

    public static DailyQuest assigned(long userId, LocalDate questDate) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }

        DailyQuest dailyQuest = new DailyQuest();
        dailyQuest.userId = userId;
        dailyQuest.questDate = Objects.requireNonNull(
                questDate,
                "questDate must not be null"
        );
        dailyQuest.status = DailyQuestStatus.ASSIGNED;
        dailyQuest.totalCount = TOTAL_QUESTION_COUNT;
        dailyQuest.correctCount = 0;
        dailyQuest.score = 0;
        return dailyQuest;
    }

    public Long getDailyQuestId() {
        return dailyQuestId;
    }

    public void setDailyQuestId(Long dailyQuestId) {
        this.dailyQuestId = dailyQuestId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public LocalDate getQuestDate() {
        return questDate;
    }

    public void setQuestDate(LocalDate questDate) {
        this.questDate = questDate;
    }

    public DailyQuestStatus getStatus() {
        return status;
    }

    public void setStatus(DailyQuestStatus status) {
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

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
