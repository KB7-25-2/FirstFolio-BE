package org.firstfolio.learning.domain;

import java.time.LocalDateTime;

public class UserCurriculumItem {

    private long curriculumItemId;
    private long userId;
    private long mainChapterId;
    private String status;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
