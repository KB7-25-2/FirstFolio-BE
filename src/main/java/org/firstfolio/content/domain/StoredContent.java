package org.firstfolio.content.domain;

import java.util.Objects;

/**
 * 저장소에서 조회한 정적 콘텐츠 본문과 미디어 타입이다.
 */
public record StoredContent(
        byte[] content,
        String contentType
) {

    public StoredContent {
        Objects.requireNonNull(content, "content must not be null");

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }

        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
