package org.firstfolio.auth.web;

import org.firstfolio.common.response.ApiResponse;
import org.firstfolio.auth.annotation.CurrentUser;
import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.domain.VerifiedFirebaseUser;
import org.firstfolio.auth.interceptor.FirebaseAuthenticationInterceptor;
import org.firstfolio.auth.service.BearerTokenExtractor;
import org.firstfolio.auth.service.FirebaseTokenVerifier;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.user.domain.User;
import org.firstfolio.user.domain.UserRole;
import org.firstfolio.user.domain.UserStatus;
import org.firstfolio.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurrentUserAuthenticationTest {

    private FakeUserMapper userMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userMapper = new FakeUserMapper();
        FirebaseTokenVerifier tokenVerifier = token ->
                new VerifiedFirebaseUser(
                        "firebase-uid-1",
                        "student@example.com"
                );
        FirebaseAuthenticationInterceptor interceptor =
                new FirebaseAuthenticationInterceptor(
                        new BearerTokenExtractor(),
                        tokenVerifier,
                        userMapper
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProtectedTestController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
                )
                .addFilter(new RequestIdFilter())
                .addFilter(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    void injectsFirstFolioUserFromVerifiedFirebaseToken() throws Exception {
        userMapper.foundUser = user(UserStatus.ACTIVE);

        mockMvc.perform(
                        get("/test/current-user")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer valid-token"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(
                        "{\"data\":{\"user_id\":101,\"role_code\":\"USER\"}}"
                ));
    }

    @Test
    void rejectsRequestWithoutFirebaseToken() throws Exception {
        mockMvc.perform(get("/test/current-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString(
                        "\"code\":\"UNAUTHORIZED\""
                )));
    }

    @Test
    void rejectsInactiveFirstFolioUser() throws Exception {
        userMapper.foundUser = user(UserStatus.SUSPENDED);

        mockMvc.perform(
                        get("/test/current-user")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer valid-token"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString(
                        "\"code\":\"ACCOUNT_NOT_ACTIVE\""
                )));
    }

    private User user(UserStatus status) {
        User user = User.signup(
                "firebase-uid-1",
                "student@example.com",
                "새싹투자자",
                LocalDateTime.of(2026, 8, 1, 0, 0)
        );
        user.setUserId(101L);
        user.setStatus(status);
        return user;
    }

    @RestController
    public static class ProtectedTestController {

        @GetMapping("/test/current-user")
        public ApiResponse<CurrentUserResponse> currentUser(
                @CurrentUser AuthenticatedUser currentUser
        ) {
            return ApiResponse.of(
                    new CurrentUserResponse(
                            currentUser.userId(),
                            currentUser.roleCode()
                    )
            );
        }
    }

    public record CurrentUserResponse(long userId, UserRole roleCode) {
    }

    private static final class FakeUserMapper implements UserMapper {

        private User foundUser;

        @Override
        public int countSignupConflicts(
                String firebaseUid,
                String email,
                String nickname
        ) {
            return 0;
        }

        @Override
        public int insert(User user) {
            return 0;
        }

        @Override
        public User findByFirebaseUid(String firebaseUid) {
            return foundUser;
        }

        @Override
        public int updateLastLoginAt(long userId, LocalDateTime lastLoginAt) {
            return 0;
        }

        @Override
        public String findOnboardingStep(long userId) {
            return "HOME";
        }
    }
}
