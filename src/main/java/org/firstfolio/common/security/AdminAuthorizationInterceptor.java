package org.firstfolio.common.security;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@code /admin/**} 요청에 ADMIN 권한을 요구한다.
 *
 * <p>화면에서 감췄는지와 무관하게 서버에서 검증한다 (PROJECT_SPEC 16장).</p>
 */
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        CurrentUser currentUser = StubUserHeaders.read(request)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED));

        if (!currentUser.isAdmin()) {
            throw new ApiException(ErrorCode.ADMIN_REQUIRED);
        }

        return true;
    }
}
