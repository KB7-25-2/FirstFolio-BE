package org.firstfolio.curriculum.domain;

public class PublicSubChapter {

    private long subChapterId;
    private long mainChapterId;
    private String title;
    private String description;
    private int displayOrder;
    private boolean contentAvailable;

    public PublicSubChapter() {
    }

    public long getSubChapterId() {
        return subChapterId;
    }

    public void setSubChapterId(long subChapterId) {
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

    public boolean isContentAvailable() {
        return contentAvailable;
    }

    public void setContentAvailable(boolean contentAvailable) {
        this.contentAvailable = contentAvailable;
    }
}
