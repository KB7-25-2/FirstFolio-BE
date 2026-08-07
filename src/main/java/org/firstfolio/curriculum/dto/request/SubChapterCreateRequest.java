package org.firstfolio.curriculum.dto.request;

public record SubChapterCreateRequest(
        String title,
        String description,
        Integer displayOrder
) {
}
