package org.firstfolio.common.web;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("요청마다 req- 접두사를 가진 식별자를 발급하고 응답 헤더에도 넣는다")
    void issuesRequestIdAndEchoesItInResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);

        assertTrue(requestId.startsWith("req-"), "req- 접두사가 있어야 합니다.");
        assertEquals(requestId, response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    @DisplayName("요청마다 서로 다른 식별자를 발급한다")
    void issuesDistinctRequestIds() throws Exception {
        MockHttpServletRequest first = new MockHttpServletRequest();
        MockHttpServletRequest second = new MockHttpServletRequest();

        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(second, new MockHttpServletResponse(), new MockFilterChain());

        assertNotEquals(
                first.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE),
                second.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)
        );
    }

    @Test
    @DisplayName("요청이 끝나면 로그 컨텍스트를 비운다")
    void clearsLogContextAfterRequest() throws Exception {
        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertNull(ThreadContext.get(RequestIdFilter.REQUEST_ID_LOG_KEY));
    }
}
