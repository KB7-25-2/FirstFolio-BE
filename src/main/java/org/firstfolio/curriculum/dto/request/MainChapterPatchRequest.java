package org.firstfolio.curriculum.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;

public final class MainChapterPatchRequest {

    private String title;
    private String description;
    private Integer displayOrder;
    private Boolean active;
    private boolean titleProvided;
    private boolean descriptionProvided;
    private boolean displayOrderProvided;
    private boolean activeProvided;

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
        this.titleProvided = true;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    @JsonSetter("display_order")
    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        this.displayOrderProvided = true;
    }

    @JsonSetter("is_active")
    public void setActive(Boolean active) {
        this.active = active;
        this.activeProvided = true;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Integer displayOrder() {
        return displayOrder;
    }

    public Boolean active() {
        return active;
    }

    public boolean titleProvided() {
        return titleProvided;
    }

    public boolean descriptionProvided() {
        return descriptionProvided;
    }

    public boolean displayOrderProvided() {
        return displayOrderProvided;
    }

    public boolean activeProvided() {
        return activeProvided;
    }

    public boolean hasAnyField() {
        return titleProvided
                || descriptionProvided
                || displayOrderProvided
                || activeProvided;
    }
}
