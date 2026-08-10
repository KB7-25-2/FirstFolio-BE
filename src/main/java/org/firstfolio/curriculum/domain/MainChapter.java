package org.firstfolio.curriculum.domain;

import java.time.LocalDateTime;

public class MainChapter {

    private Long mainChapterId;
    private ChapterType chapterType;
    private AssetType assetType;
    private String title;
    private String description;
    private int displayOrder;
    private boolean required;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MainChapter() {
    }

    public static MainChapter create(
            ChapterType chapterType,
            AssetType assetType,
            String title,
            String description,
            int displayOrder,
            boolean required,
            boolean active,
            LocalDateTime now
    ) {
        MainChapter chapter = new MainChapter();
        chapter.chapterType = chapterType;
        chapter.assetType = assetType;
        chapter.title = title;
        chapter.description = description;
        chapter.displayOrder = displayOrder;
        chapter.required = required;
        chapter.active = active;
        chapter.createdAt = now;
        chapter.updatedAt = now;
        return chapter;
    }

    public Long getMainChapterId() {
        return mainChapterId;
    }

    public void setMainChapterId(Long mainChapterId) {
        this.mainChapterId = mainChapterId;
    }

    public ChapterType getChapterType() {
        return chapterType;
    }

    public void setChapterType(ChapterType chapterType) {
        this.chapterType = chapterType;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
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

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
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
