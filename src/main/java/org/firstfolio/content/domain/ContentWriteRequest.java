package org.firstfolio.content.domain;

import java.util.Objects;

/**
 * 정적 콘텐츠 저장소에 새 객체 버전을 저장하기 위한 요청이다.
 */
public record ContentWriteRequest(
        String objectKey,
        byte[] content,
        String contentType
) {

    public ContentWriteRequest {
        objectKey = requireText(objectKey, "objectKey");
        contentType = requireText(contentType, "contentType");
        Objects.requireNonNull(content, "content must not be null");

        if (content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }

        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
