package org.firstfolio.common.security;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Firebase 인증 인터셉터가 요청에 저장한 FirstFolio 사용자를 서비스 계층에 제공한다.
 */
@Component
public class RequestAttributeCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<AuthenticatedUser> find() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();

        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }

        Object currentUser = servletAttributes.getRequest().getAttribute(
                AuthenticationRequestAttributes.CURRENT_USER
        );

        return currentUser instanceof AuthenticatedUser authenticatedUser
                ? Optional.of(authenticatedUser)
                : Optional.empty();
    }
}
