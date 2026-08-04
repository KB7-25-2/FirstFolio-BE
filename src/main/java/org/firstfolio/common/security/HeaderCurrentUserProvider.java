package org.firstfolio.common.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * {@link StubUserHeaders} 기반 임시 구현.
 *
 * <p><b>TODO: Identity 도메인 구현 후 실제 인증 구현체로 교체한다.</b>
 * 교체 시 이 클래스만 지우면 되고, 이 인터페이스를 쓰는 서비스는 손대지 않는다.</p>
 */
@Component
public class HeaderCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<CurrentUser> find() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (!(attributes instanceof ServletRequestAttributes)) {
            return Optional.empty();
        }

        return StubUserHeaders.read(((ServletRequestAttributes) attributes).getRequest());
    }
}
