package org.firstfolio.common.security;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizationInterceptorTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new GuardedController())
            .setControllerAdvice(new CommonExceptionAdvice())
            .setMessageConverters(
                    new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
            )
            .addFilters(new RequestIdFilter())
            .addMappedInterceptors(
                    new String[]{"/admin/**"},
                    new AdminAuthorizationInterceptor()
            )
            .addMappedInterceptors(
                    new String[]{"/internal/**"},
                    new InternalCallInterceptor(INTERNAL_TOKEN)
            )
            .build();

    @Test
    @DisplayName("관리자 경로는 인증이 없으면 401로 막는다")
    void rejectsAdminPathWithoutUser() throws Exception {
        mockMvc.perform(get("/admin/things"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("일반 사용자는 관리자 경로에서 ADMIN_REQUIRED로 막힌다")
    void rejectsAdminPathForNormalUser() throws Exception {
        mockMvc.perform(get("/admin/things")
                        .requestAttr(
                                AuthenticationRequestAttributes.CURRENT_USER,
                                authenticatedUser(UserRole.USER)
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_REQUIRED"));
    }

    @Test
    @DisplayName("ADMIN 역할이면 관리자 경로를 통과한다")
    void allowsAdmin() throws Exception {
        mockMvc.perform(get("/admin/things")
                        .requestAttr(
                                AuthenticationRequestAttributes.CURRENT_USER,
                                authenticatedUser(UserRole.ADMIN)
                        ))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("내부 경로는 토큰이 없으면 막는다")
    void rejectsInternalPathWithoutToken() throws Exception {
        mockMvc.perform(get("/internal/things"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_CALL_REQUIRED"));
    }

    @Test
    @DisplayName("내부 경로는 토큰이 틀리면 막는다")
    void rejectsInternalPathWithWrongToken() throws Exception {
        mockMvc.perform(get("/internal/things")
                        .header(InternalCallInterceptor.INTERNAL_TOKEN_HEADER, "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_CALL_REQUIRED"));
    }

    @Test
    @DisplayName("토큰이 맞으면 내부 경로를 통과한다")
    void allowsInternalCallWithToken() throws Exception {
        mockMvc.perform(get("/internal/things")
                        .header(InternalCallInterceptor.INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰이 설정돼 있지 않으면 내부 호출을 전부 거부한다")
    void deniesInternalCallWhenTokenNotConfigured() throws Exception {
        MockMvc unconfigured = MockMvcBuilders
                .standaloneSetup(new GuardedController())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
                )
                .addFilters(new RequestIdFilter())
                .addMappedInterceptors(
                        new String[]{"/internal/**"},
                        new InternalCallInterceptor("")
                )
                .build();

        unconfigured.perform(get("/internal/things")
                        .header(InternalCallInterceptor.INTERNAL_TOKEN_HEADER, "anything"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_CALL_REQUIRED"));
    }

    private static AuthenticatedUser authenticatedUser(UserRole role) {
        return new AuthenticatedUser(1L, "firebase-uid-1", "테스트사용자", role);
    }

    @RestController
    static class GuardedController {

        @GetMapping("/admin/things")
        public String adminThing() {
            return "ok";
        }

        @GetMapping("/internal/things")
        public String internalThing() {
            return "ok";
        }
    }
}
