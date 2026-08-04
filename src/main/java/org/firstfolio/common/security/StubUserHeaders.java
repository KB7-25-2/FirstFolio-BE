package org.firstfolio.common.security;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * 임시 인증 스텁. 요청 헤더를 그대로 사용자 정보로 받아들인다.
 *
 * <p><b>TODO: Identity 도메인(인증·인가) 구현 후 제거한다.</b> 헤더만 바꾸면 누구든
 * 관리자가 될 수 있으므로 <b>운영 환경에 이 상태로 배포하면 안 된다.</b>
 * Portfolio 도메인 작업을 인증 구현과 병행하기 위한 임시 수단이다.</p>
 */
public final class StubUserHeaders {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private StubUserHeaders() {
    }

    public static Optional<CurrentUser> read(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        String rawUserId = request.getHeader(USER_ID_HEADER);

        if (rawUserId == null || rawUserId.isBlank()) {
            return Optional.empty();
        }

        long userId;

        try {
            userId = Long.parseLong(rawUserId.trim());
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        if (userId <= 0) {
            return Optional.empty();
        }

        return Optional.of(new CurrentUser(
                userId,
                UserRole.from(request.getHeader(USER_ROLE_HEADER))
        ));
    }
}
