package org.firstfolio.learning.domain;

import java.time.LocalDateTime;

public class LearningProgress {

    private Long progressId;
    private long userId;
    private long subChapterId;
    private long contentVersionId;
    private String lastPageId;
    private LearningProgressStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;

    public Long getProgressId() {
        return progressId;
    }

    public void setProgressId(Long progressId) {
        this.progressId = progressId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getSubChapterId() {
        return subChapterId;
    }

    public void setSubChapterId(long subChapterId) {
        this.subChapterId = subChapterId;
    }

    public long getContentVersionId() {
        return contentVersionId;
    }

    public void setContentVersionId(long contentVersionId) {
        this.contentVersionId = contentVersionId;
    }

    public String getLastPageId() {
        return lastPageId;
    }

    public void setLastPageId(String lastPageId) {
        this.lastPageId = lastPageId;
    }

    public LearningProgressStatus getStatus() {
        return status;
    }

    public void setStatus(LearningProgressStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
