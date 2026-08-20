package org.firstfolio.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** 모든 HTTP 요청을 정규화된 Spring 경로 패턴 단위로 측정한다. */
public class HttpRequestMetricsFilter implements Filter {

    public static final String BEAN_NAME = "httpRequestMetricsFilter";
    private static final String METRICS_PATH = "/internal/metrics";

    private final PrometheusMeterRegistry registry;

    public HttpRequestMetricsFilter(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)
                || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (METRICS_PATH.equals(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        long startedAt = System.nanoTime();
        boolean failed = false;

        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            int status = failed && httpResponse.getStatus() < 500
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    : httpResponse.getStatus();

            record(
                    httpRequest,
                    status,
                    System.nanoTime() - startedAt
            );
        }
    }

    private void record(
            HttpServletRequest request,
            int status,
            long durationNanos
    ) {
        Timer.builder("http.server.requests")
                .description("HTTP server request duration")
                .tag("method", request.getMethod())
                .tag("uri", routePattern(request, status))
                .tag("status", Integer.toString(status))
                .tag("outcome", outcome(status))
                .publishPercentileHistogram()
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private static String routePattern(HttpServletRequest request, int status) {
        Object pattern = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
        );

        if (pattern instanceof String && !((String) pattern).isBlank()) {
            return (String) pattern;
        }

        return status == HttpServletResponse.SC_NOT_FOUND
                ? "NOT_FOUND"
                : "UNKNOWN";
    }

    private static String outcome(int status) {
        if (status >= 100 && status < 200) {
            return "INFORMATIONAL";
        }
        if (status < 300) {
            return "SUCCESS";
        }
        if (status < 400) {
            return "REDIRECTION";
        }
        if (status < 500) {
            return "CLIENT_ERROR";
        }
        if (status < 600) {
            return "SERVER_ERROR";
        }
        return "UNKNOWN";
    }
}
