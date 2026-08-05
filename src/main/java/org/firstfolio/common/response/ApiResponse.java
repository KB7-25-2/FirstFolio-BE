package org.firstfolio.common.response;

/**
 * API_DOCS.md의 모든 성공 응답은 {@code {"data": ...}} 한 겹으로 감싼다.
 */
public final class ApiResponse<T> {

    private final T data;

    private ApiResponse(T data) {
        this.data = data;
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }

    public T getData() {
        return data;
    }
}
