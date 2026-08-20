package org.firstfolio.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpRequestMetricsFilterTest {

    @Test
    void recordsNormalizedRouteInsteadOfRawResourceId() throws Exception {
        PrometheusMeterRegistry registry = registry();

        try {
            HttpRequestMetricsFilter filter = new HttpRequestMetricsFilter(registry);
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET",
                    "/api/quizzes/12345"
            );
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                servletRequest.setAttribute(
                        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                        "/api/quizzes/{quizId}"
                );
                ((HttpServletResponse) servletResponse).setStatus(200);
            });

            Timer timer = registry.get("http.server.requests")
                    .tags(
                            "method", "GET",
                            "uri", "/api/quizzes/{quizId}",
                            "status", "200",
                            "outcome", "SUCCESS"
                    )
                    .timer();

            assertEquals(1L, timer.count());
            assertNull(
                    registry.find("http.server.requests")
                            .tag("uri", "/api/quizzes/12345")
                            .timer()
            );
        } finally {
            registry.close();
        }
    }

    @Test
    void excludesMetricsScrapeFromApplicationRequestMetrics() throws Exception {
        PrometheusMeterRegistry registry = registry();

        try {
            HttpRequestMetricsFilter filter = new HttpRequestMetricsFilter(registry);
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET",
                    "/internal/metrics"
            );
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            });

            assertNull(registry.find("http.server.requests").timer());
        } finally {
            registry.close();
        }
    }

    private static PrometheusMeterRegistry registry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
}
