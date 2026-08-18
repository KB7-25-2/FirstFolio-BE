package org.firstfolio.config;

import org.firstfolio.auth.interceptor.FirebaseAuthenticationInterceptor;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ServletConfigCorsTest {

    private static final String LOCAL_ORIGIN = "http://localhost:5173";

    @Test
    @DisplayName("쉼표로 구분한 출처를 /api 전체 경로에 등록한다")
    void registersConfiguredOriginsForApiPaths() {
        CorsConfiguration configuration = configuration(
                "http://localhost:5173, http://127.0.0.1:5173"
        );

        assertEquals(
                java.util.List.of(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                ),
                configuration.getAllowedOrigins()
        );
        assertEquals(Boolean.FALSE, configuration.getAllowCredentials());
        assertEquals(3600L, configuration.getMaxAge());
        assertTrue(configuration.getAllowedHeaders().contains("Idempotency-Key"));
    }

    @Test
    @DisplayName("허용된 출처의 Authorization preflight 요청을 처리한다")
    void allowsAuthorizationPreflightFromConfiguredOrigin() throws Exception {
        MockHttpServletRequest request = preflightRequest(LOCAL_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean processed = new DefaultCorsProcessor().processRequest(
                configuration(LOCAL_ORIGIN),
                request,
                response
        );

        assertTrue(processed);
        assertEquals(LOCAL_ORIGIN, response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(
                response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).contains("POST")
        );
        assertTrue(
                response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                        .toLowerCase()
                        .contains("authorization")
        );
    }

    @Test
    @DisplayName("등록되지 않은 출처의 preflight 요청을 거부한다")
    void rejectsPreflightFromUnknownOrigin() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean processed = new DefaultCorsProcessor().processRequest(
                configuration(LOCAL_ORIGIN),
                preflightRequest("https://malicious.example"),
                response
        );

        assertFalse(processed);
        assertEquals(403, response.getStatus());
        assertFalse(response.containsHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("허용된 출처에 X-Request-Id 응답 헤더를 노출한다")
    void exposesRequestIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(HttpHeaders.ORIGIN, LOCAL_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean processed = new DefaultCorsProcessor().processRequest(
                configuration(LOCAL_ORIGIN),
                request,
                response
        );

        assertTrue(processed);
        assertEquals(
                "X-Request-Id",
                response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)
        );
    }

    @Test
    @DisplayName("전체 출처를 허용하는 와일드카드 설정을 거부한다")
    void rejectsWildcardOriginConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> configuration("*")
        );
    }

    @Test
    @DisplayName("허용 출처가 비어 있는 설정을 거부한다")
    void rejectsEmptyOriginConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> configuration(" , ")
        );
    }

    private static MockHttpServletRequest preflightRequest(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS",
                "/api/auth/login"
        );
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                "Authorization, Content-Type"
        );

        return request;
    }

    private static CorsConfiguration configuration(String origins) {
        InspectableCorsRegistry registry = new InspectableCorsRegistry();

        new ServletConfig(
                mock(FirebaseAuthenticationInterceptor.class),
                mock(CurrentUserArgumentResolver.class),
                origins
        ).addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/api/**");
        assertNotNull(configuration);

        return configuration;
    }

    private static final class InspectableCorsRegistry extends CorsRegistry {

        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
