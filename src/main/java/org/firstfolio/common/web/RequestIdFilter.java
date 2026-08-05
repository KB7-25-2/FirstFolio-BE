package org.firstfolio.common.web;

import org.apache.logging.log4j.ThreadContext;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 추적 식별자를 발급한다.
 *
 * <p>API_DOCS.md의 오류 응답은 {@code request_id}를 필수로 요구한다. 컨트롤러마다
 * 만들지 않도록 여기서 한 번 발급해 요청 속성과 로그 컨텍스트에 넣어 두고,
 * {@code CommonExceptionAdvice}가 꺼내 쓴다.</p>
 *
 * <p>정상 응답에는 {@code request_id}가 본문에 없으므로 응답 헤더
 * {@code X-Request-Id}로도 같은 값을 내보낸다.</p>
 */
public class RequestIdFilter implements Filter {

    public static final String REQUEST_ID_ATTRIBUTE = "org.firstfolio.requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_LOG_KEY = "requestId";

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        String requestId = generate();

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        ThreadContext.put(REQUEST_ID_LOG_KEY, requestId);

        if (response instanceof HttpServletResponse) {
            ((HttpServletResponse) response).setHeader(REQUEST_ID_HEADER, requestId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            ThreadContext.remove(REQUEST_ID_LOG_KEY);
        }
    }

    /**
     * 요청 속성에서 추적 식별자를 읽는다. 필터를 거치지 않은 경로에서도
     * 응답을 만들 수 있도록 없으면 새로 만들어 준다.
     */
    public static String currentRequestId(HttpServletRequest request) {
        if (request == null) {
            return generate();
        }

        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);

        return value instanceof String ? (String) value : generate();
    }

    private static String generate() {
        return "req-" + UUID.randomUUID().toString().replace("-", "");
    }
}
