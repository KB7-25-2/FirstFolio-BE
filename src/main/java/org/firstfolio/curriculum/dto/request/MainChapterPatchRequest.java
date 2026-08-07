package org.firstfolio.curriculum.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대단원 부분 수정. 전달한 필드만 변경")
public final class MainChapterPatchRequest {

    @Schema(description = "변경할 제목", example = "예·적금 기초")
    private String title;
    @Schema(description = "변경할 소개", example = "예금과 적금의 핵심을 학습합니다.")
    private String description;
    @Schema(description = "변경할 노출 순서", example = "2")
    private Integer displayOrder;
    @Schema(description = "활성 여부", example = "true")
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
