package org.firstfolio.config;

import org.firstfolio.common.security.AdminAuthorizationInterceptor;
import org.firstfolio.common.security.InternalCallInterceptor;
import org.firstfolio.auth.interceptor.FirebaseAuthenticationInterceptor;
import org.firstfolio.auth.web.CurrentUserArgumentResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@link ServletConfig}에 실제로 등록된 경로 패턴을 검증한다.
 *
 * <p>기존 인터셉터 테스트는 테스트 코드가 직접 만든 매핑을 검증해서, 설정의 패턴이 틀려도
 * 통과했다. 실제로 {@code /admin/**}만 걸려 있어 {@code /api} 접두사가 붙은 요청이 전부
 * 검증을 통과해버리는 결함을 잡지 못했다. 그래서 설정 객체에서 패턴을 직접 읽어 확인한다.</p>
 */
class ServletConfigInterceptorPathTest {

    @Test
    @DisplayName("Firebase 인증 인터셉터는 /api/auth만 공개 경로로 제외한다")
    void firebaseAuthenticationExcludesOnlyApiAuthPath() {
        MappedInterceptor interceptor = interceptorFor(
                FirebaseAuthenticationInterceptor.class
        );

        assertFalse(interceptor.matches(request("/api/auth/signup")));
        assertFalse(interceptor.matches(request("/api/auth/login")));
        assertFalse(interceptor.matches(request("/api/auth/logout")));
        assertTrue(interceptor.matches(request("/auth/login")));
    }

    @ParameterizedTest(name = "관리자 인터셉터가 {0} 를 가로챈다")
    @DisplayName("관리자 경로는 /api 접두사가 붙어도 가로챈다")
    @ValueSource(strings = {
            "/api/admin/financial-products",
            "/api/admin/financial-products/25",
            "/api/admin/financial-products/imports",
            "/admin/financial-products"
    })
    void adminInterceptorCoversAdminPaths(String path) {
        assertTrue(
                interceptorFor(AdminAuthorizationInterceptor.class).matches(request(path)),
                path + " 가 권한 검증 없이 통과합니다."
        );
    }

    @ParameterizedTest(name = "내부 호출 인터셉터가 {0} 를 가로챈다")
    @DisplayName("내부 배치 경로는 /api 접두사가 붙어도 가로챈다")
    @ValueSource(strings = {
            "/api/internal/product-prices/refresh",
            "/api/internal/portfolio-events/process",
            "/internal/product-prices/refresh"
    })
    void internalInterceptorCoversInternalPaths(String path) {
        assertTrue(
                interceptorFor(InternalCallInterceptor.class).matches(request(path)),
                path + " 가 내부 호출 검증 없이 통과합니다."
        );
    }

    @ParameterizedTest(name = "{0} 는 권한 검증 대상이 아니다")
    @DisplayName("일반 사용자 경로에는 관리자·내부 검증을 걸지 않는다")
    @ValueSource(strings = {
            "/api/health",
            "/api/financial-products",
            "/api/portfolios/current"
    })
    void doesNotGuardPublicPaths(String path) {
        assertFalse(interceptorFor(AdminAuthorizationInterceptor.class).matches(request(path)));
        assertFalse(interceptorFor(InternalCallInterceptor.class).matches(request(path)));
    }

    private static HttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);

        // MappedInterceptor는 파싱된 경로를 요청 속성에서 읽는다.
        // 실제로는 DispatcherServlet이 넣어 주지만 목 요청에는 없다.
        ServletRequestPathUtils.parseAndCache(request);

        return request;
    }

    private static MappedInterceptor interceptorFor(Class<?> interceptorType) {
        InspectableRegistry registry = new InspectableRegistry();

        new ServletConfig(
                mock(FirebaseAuthenticationInterceptor.class),
                mock(CurrentUserArgumentResolver.class)
        ).addInterceptors(registry);

        for (Object registered : registry.registered()) {
            if (registered instanceof MappedInterceptor) {
                MappedInterceptor mapped = (MappedInterceptor) registered;

                if (interceptorType.isInstance(mapped.getInterceptor())) {
                    return mapped;
                }
            }
        }

        assertNotNull(null, interceptorType.getSimpleName() + " 가 등록되지 않았습니다.");

        return null;
    }

    /** {@code getInterceptors()}가 protected라 하위 클래스로 열어 준다. */
    private static final class InspectableRegistry extends InterceptorRegistry {

        List<Object> registered() {
            return getInterceptors();
        }
    }
}
