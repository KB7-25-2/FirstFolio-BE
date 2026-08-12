package org.firstfolio.curriculum.domain;

import java.time.LocalDateTime;

public class UserCurriculumItem {

    private long curriculumItemId;
    private long userId;
    private long mainChapterId;
    private int displayOrder;
    private CurriculumSourceType sourceType;
    private CurriculumItemStatus status;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;

    public long getCurriculumItemId() {
        return curriculumItemId;
    }

    public void setCurriculumItemId(long curriculumItemId) {
        this.curriculumItemId = curriculumItemId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getMainChapterId() {
        return mainChapterId;
    }

    public void setMainChapterId(long mainChapterId) {
        this.mainChapterId = mainChapterId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public CurriculumSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(CurriculumSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public CurriculumItemStatus getStatus() {
        return status;
    }

    public void setStatus(CurriculumItemStatus status) {
        this.status = status;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
