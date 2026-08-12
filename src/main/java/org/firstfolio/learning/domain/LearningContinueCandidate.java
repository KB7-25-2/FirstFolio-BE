package org.firstfolio.learning.domain;

import java.time.LocalDateTime;

public class LearningContinueCandidate {

    private long curriculumItemId;
    private long mainChapterId;
    private boolean mainChapterActive;
    private long subChapterId;
    private boolean subChapterActive;
    private Long currentContentVersionId;
    private long contentVersionId;
    private String lastPageId;
    private LocalDateTime updatedAt;

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

    public boolean isMainChapterActive() {
        return mainChapterActive;
    }

    public void setMainChapterActive(boolean mainChapterActive) {
        this.mainChapterActive = mainChapterActive;
    }

    public long getSubChapterId() {
        return subChapterId;
    }

    public void setSubChapterId(long subChapterId) {
        this.subChapterId = subChapterId;
    }

    public boolean isSubChapterActive() {
        return subChapterActive;
    }

    public void setSubChapterActive(boolean subChapterActive) {
        this.subChapterActive = subChapterActive;
    }

    public Long getCurrentContentVersionId() {
        return currentContentVersionId;
    }

    public void setCurrentContentVersionId(Long currentContentVersionId) {
        this.currentContentVersionId = currentContentVersionId;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
