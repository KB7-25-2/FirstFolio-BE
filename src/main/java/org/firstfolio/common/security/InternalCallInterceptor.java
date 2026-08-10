package org.firstfolio.common.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * {@code /internal/**} 요청이 허용된 내부 호출인지 확인한다.
 *
 * <p>내부 배치·스케줄러만 부르는 경로라 사용자 인증과 별개로 공유 토큰으로 막는다.
 * 토큰이 설정돼 있지 않으면 <b>전부 거부한다</b> — 설정을 빠뜨렸을 때 조용히 열려 있는 것보다
 * 막히는 편이 안전하다.</p>
 */
public class InternalCallInterceptor implements HandlerInterceptor {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final Logger log = LogManager.getLogger(InternalCallInterceptor.class);

    private final String expectedToken;

    public InternalCallInterceptor(String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (expectedToken.isEmpty()) {
            log.error(
                    "내부 호출 토큰이 설정되지 않아 요청을 거부했습니다. path={} 환경변수 INTERNAL_CALL_TOKEN 확인 필요",
                    request.getRequestURI()
            );
            throw new ApiException(ErrorCode.INTERNAL_CALL_REQUIRED);
        }

        String provided = request.getHeader(INTERNAL_TOKEN_HEADER);

        if (provided == null || !matches(provided.trim())) {
            throw new ApiException(ErrorCode.INTERNAL_CALL_REQUIRED);
        }

        return true;
    }

    /** 비교 시간으로 토큰을 추측당하지 않도록 상수 시간 비교를 쓴다. */
    private boolean matches(String provided) {
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
