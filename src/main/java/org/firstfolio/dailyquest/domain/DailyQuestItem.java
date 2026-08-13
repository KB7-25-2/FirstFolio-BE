package org.firstfolio.dailyquest.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class DailyQuestItem {

    private Long dailyQuestItemId;
    private long dailyQuestId;
    private long questionId;
    private int displayOrder;
    private String questionSnapshotJson;
    private String userAnswerJson;
    private Boolean correct;
    private LocalDateTime answeredAt;
    private LocalDateTime createdAt;

    public DailyQuestItem() {
    }

    public static DailyQuestItem assigned(
            long dailyQuestId,
            long questionId,
            int displayOrder,
            String questionSnapshotJson,
            LocalDateTime createdAt
    ) {
        if (dailyQuestId <= 0) {
            throw new IllegalArgumentException("dailyQuestId must be positive");
        }
        if (questionId <= 0) {
            throw new IllegalArgumentException("questionId must be positive");
        }
        if (displayOrder < 1
                || displayOrder > DailyQuest.TOTAL_QUESTION_COUNT) {
            throw new IllegalArgumentException(
                    "displayOrder must be between 1 and 5"
            );
        }
        if (questionSnapshotJson == null || questionSnapshotJson.isBlank()) {
            throw new IllegalArgumentException(
                    "questionSnapshotJson must not be blank"
            );
        }

        DailyQuestItem item = new DailyQuestItem();
        item.dailyQuestId = dailyQuestId;
        item.questionId = questionId;
        item.displayOrder = displayOrder;
        item.questionSnapshotJson = questionSnapshotJson;
        item.createdAt = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
        return item;
    }

    public Long getDailyQuestItemId() {
        return dailyQuestItemId;
    }

    public void setDailyQuestItemId(Long dailyQuestItemId) {
        this.dailyQuestItemId = dailyQuestItemId;
    }

    public long getDailyQuestId() {
        return dailyQuestId;
    }

    public void setDailyQuestId(long dailyQuestId) {
        this.dailyQuestId = dailyQuestId;
    }

    public long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(long questionId) {
        this.questionId = questionId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getQuestionSnapshotJson() {
        return questionSnapshotJson;
    }

    public void setQuestionSnapshotJson(String questionSnapshotJson) {
        this.questionSnapshotJson = questionSnapshotJson;
    }

    public String getUserAnswerJson() {
        return userAnswerJson;
    }

    public void setUserAnswerJson(String userAnswerJson) {
        this.userAnswerJson = userAnswerJson;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
