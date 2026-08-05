package org.firstfolio.auth.controller;

import org.firstfolio.auth.domain.OnboardingStep;
import org.firstfolio.auth.dto.request.SignupRequest;
import org.firstfolio.auth.service.AuthUseCase;
import org.firstfolio.auth.service.LoginResult;
import org.firstfolio.auth.service.SignupResult;
import org.firstfolio.common.json.ApiObjectMapperFactory;
import org.firstfolio.common.web.RequestIdFilter;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.CommonExceptionAdvice;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private StubAuthUseCase authUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authUseCase = new StubAuthUseCase();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authUseCase))
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(ApiObjectMapperFactory.create())
                )
                .addFilter(new RequestIdFilter())
                .addFilter(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    void signupReturnsCreatedUser() throws Exception {
        mockMvc.perform(
                        post("/api/auth/signup")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer valid-token"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "nickname": "새싹투자자",
                                          "required_terms_agreed": true
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(
                        "{\"data\":{\"user_id\":101,\"nickname\":\"새싹투자자\","
                                + "\"role_code\":\"USER\","
                                + "\"onboarding_step\":\"LEVEL_TEST\"}}"
                ));
    }

    @Test
    void loginReturnsUserAndNextStep() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer valid-token"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(
                        "{\"data\":{\"user\":{\"user_id\":101,"
                                + "\"nickname\":\"새싹투자자\","
                                + "\"role_code\":\"USER\"},"
                                + "\"onboarding_step\":\"CURRICULUM\"}}"
                ));
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        mockMvc.perform(
                        post("/api/auth/logout")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer valid-token"
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void apiErrorUsesCommonErrorEnvelope() throws Exception {
        authUseCase.failure = new ApiException(ErrorCode.INVALID_ID_TOKEN);

        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(content().string(containsString(
                        "\"code\":\"INVALID_ID_TOKEN\""
                )))
                .andExpect(content().string(containsString(
                        "\"request_id\":\"req-"
                )));
    }

    @Test
    void malformedSignupBodyReturnsSignupInputError() throws Exception {
        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not-json}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString(
                        "\"code\":\"INVALID_SIGNUP_INPUT\""
                )));
    }

    private static final class StubAuthUseCase implements AuthUseCase {

        private ApiException failure;

        @Override
        public SignupResult signup(
                String authorizationHeader,
                SignupRequest request
        ) {
            throwIfFailed();
            return new SignupResult(
                    101L,
                    request.nickname(),
                    UserRole.USER,
                    OnboardingStep.LEVEL_TEST
            );
        }

        @Override
        public LoginResult login(String authorizationHeader) {
            throwIfFailed();
            return new LoginResult(
                    101L,
                    "새싹투자자",
                    UserRole.USER,
                    OnboardingStep.CURRICULUM
            );
        }

        @Override
        public void logout(String authorizationHeader) {
            throwIfFailed();
        }

        private void throwIfFailed() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
