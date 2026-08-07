package org.firstfolio.content.domain;

/**
 * 저장소 종류와 관계없이 정적 콘텐츠의 특정 불변 버전을 식별한다.
 */
public record StoredObjectRef(
        String objectKey,
        String versionId
) {

    public StoredObjectRef {
        objectKey = requireText(objectKey, "objectKey");
        versionId = requireText(versionId, "versionId");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
