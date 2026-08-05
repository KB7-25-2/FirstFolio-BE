package org.firstfolio.common.security;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;

import java.util.Optional;

/**
 * 현재 요청의 사용자를 알려준다.
 *
 * <p><b>Identity 도메인이 나오면 구현체만 교체하면 되도록 인터페이스로 둔다.</b>
 * 서비스 계층은 이 인터페이스에만 의존하고, 헤더·토큰·세션 같은 인증 수단을 알지 못한다.</p>
 */
public interface CurrentUserProvider {

    Optional<AuthenticatedUser> find();

    default AuthenticatedUser require() {
        return find().orElseThrow(
                () -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED)
        );
    }
}
