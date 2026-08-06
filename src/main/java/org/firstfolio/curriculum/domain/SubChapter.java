package org.firstfolio.curriculum.domain;

import java.time.LocalDateTime;

public class SubChapter {

    private Long subChapterId;
    private long mainChapterId;
    private String title;
    private String description;
    private int displayOrder;
    private Long currentContentVersionId;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SubChapter() {
    }

    public static SubChapter create(
            long mainChapterId,
            String title,
            String description,
            int displayOrder,
            boolean active,
            LocalDateTime now
    ) {
        SubChapter chapter = new SubChapter();
        chapter.mainChapterId = mainChapterId;
        chapter.title = title;
        chapter.description = description;
        chapter.displayOrder = displayOrder;
        chapter.active = active;
        chapter.createdAt = now;
        chapter.updatedAt = now;
        return chapter;
    }

    public Long getSubChapterId() {
        return subChapterId;
    }

    public void setSubChapterId(Long subChapterId) {
        this.subChapterId = subChapterId;
    }

    public long getMainChapterId() {
        return mainChapterId;
    }

    public void setMainChapterId(long mainChapterId) {
        this.mainChapterId = mainChapterId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Long getCurrentContentVersionId() {
        return currentContentVersionId;
    }

    public void setCurrentContentVersionId(Long currentContentVersionId) {
        this.currentContentVersionId = currentContentVersionId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
