package org.firstfolio.learning.domain;

import java.time.LocalDateTime;

public class LearningProgressEvent {

    private Long progressEventId;
    private long progressId;
    private long userId;
    private long subChapterId;
    private long contentVersionId;
    private String eventType;
    private LearningProgressStatus previousStatus;
    private LearningProgressStatus status;
    private String previousPageId;
    private String lastPageId;
    private LocalDateTime occurredAt;

    public Long getProgressEventId() {
        return progressEventId;
    }

    public void setProgressEventId(Long progressEventId) {
        this.progressEventId = progressEventId;
    }

    public long getProgressId() {
        return progressId;
    }

    public void setProgressId(long progressId) {
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LearningProgressStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(LearningProgressStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public LearningProgressStatus getStatus() {
        return status;
    }

    public void setStatus(LearningProgressStatus status) {
        this.status = status;
    }

    public String getPreviousPageId() {
        return previousPageId;
    }

    public void setPreviousPageId(String previousPageId) {
        this.previousPageId = previousPageId;
    }

    public String getLastPageId() {
        return lastPageId;
    }

    public void setLastPageId(String lastPageId) {
        this.lastPageId = lastPageId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
