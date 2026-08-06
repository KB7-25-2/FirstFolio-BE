package org.firstfolio.content.exception;

/**
 * 파일 시스템이나 AWS SDK의 구현 예외를 저장소 공통 의미로 변환한다.
 */
public enum ContentStorageError {
    OBJECT_NOT_FOUND,
    INVALID_OBJECT_KEY,
    CONTENT_TOO_LARGE,
    STORAGE_UNAVAILABLE,
    STORAGE_CONFIGURATION_ERROR
}
