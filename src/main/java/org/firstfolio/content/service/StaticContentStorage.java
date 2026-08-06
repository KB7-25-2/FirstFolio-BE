package org.firstfolio.content.service;

import org.firstfolio.content.domain.ContentWriteRequest;
import org.firstfolio.content.domain.StoredContent;
import org.firstfolio.content.domain.StoredObjectRef;

/**
 * 버전형 정적 콘텐츠 저장소의 공통 계약이다.
 *
 * <p>각 저장 요청은 기존 버전을 덮어쓰지 않고 새로운 불변 버전을 만들어야 한다.</p>
 */
public interface StaticContentStorage extends AutoCloseable {

    StoredObjectRef store(ContentWriteRequest request);

    StoredContent load(StoredObjectRef reference);

    @Override
    default void close() {
        // 리소스를 보유하지 않는 저장소 구현은 별도 종료 작업이 없다.
    }
}
