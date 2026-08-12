package org.firstfolio.auth.service;

import org.firstfolio.auth.domain.OnboardingStep;
import org.firstfolio.auth.domain.VerifiedFirebaseUser;
import org.firstfolio.auth.dto.request.SignupRequest;
import org.firstfolio.auth.exception.InvalidFirebaseTokenException;
import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.ConsentType;
import org.firstfolio.user.domain.User;
import org.firstfolio.user.domain.UserConsent;
import org.firstfolio.user.domain.UserStatus;
import org.firstfolio.user.mapper.UserConsentMapper;
import org.firstfolio.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T00:00:00Z"),
            ZoneOffset.UTC
    );

    private FakeUserMapper userMapper;
    private FakeUserConsentMapper consentMapper;
    private FirebaseTokenVerifier tokenVerifier;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = new FakeUserMapper();
        consentMapper = new FakeUserConsentMapper();
        tokenVerifier = token -> new VerifiedFirebaseUser(
                "firebase-uid-1",
                "student@example.com"
        );
        authService = createService(tokenVerifier);
    }

    @Test
    void signupCreatesUserAndRequiredConsentHistory() {
        SignupResult result = authService.signup(
                "Bearer valid-token",
                new SignupRequest(" 새싹투자자 ", true)
        );

        assertEquals(101L, result.userId());
        assertEquals("새싹투자자", result.nickname());
        assertEquals(OnboardingStep.LEVEL_TEST, result.onboardingStep());
        assertEquals("firebase-uid-1", userMapper.insertedUser.getFirebaseUid());
        assertEquals("student@example.com", userMapper.insertedUser.getEmail());
        assertEquals(2, consentMapper.consents.size());
        assertEquals(
                List.of(ConsentType.TERMS_OF_SERVICE, ConsentType.PRIVACY),
                consentMapper.consents.stream()
                        .map(UserConsent::getConsentType)
                        .toList()
        );
        assertTrue(consentMapper.consents.stream().allMatch(UserConsent::isAgreed));
    }

    @Test
    void signupRejectsMissingRequiredConsent() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> authService.signup(
                        "Bearer valid-token",
                        new SignupRequest("새싹투자자", false)
                )
        );

        assertEquals(ErrorCode.INVALID_SIGNUP_INPUT, exception.getErrorCode());
        assertFalse(userMapper.insertCalled);
    }

    @Test
    void signupRejectsConflictingAccount() {
        userMapper.signupConflictCount = 1;

        ApiException exception = assertThrows(
                ApiException.class,
                () -> authService.signup(
                        "Bearer valid-token",
                        new SignupRequest("새싹투자자", true)
                )
        );

        assertEquals(ErrorCode.ACCOUNT_CONFLICT, exception.getErrorCode());
        assertFalse(userMapper.insertCalled);
    }

    @ParameterizedTest
    @CsvSource({
            "LEVEL_TEST, LEVEL_TEST",
            "CURRICULUM, CURRICULUM",
            "HOME, HOME"
    })
    void loginReturnsOnboardingStepFromPersistedProgress(
            String storedStep,
            OnboardingStep expectedStep
    ) {
        userMapper.foundUser = activeUser();
        userMapper.onboardingStep = storedStep;

        LoginResult result = authService.login("Bearer valid-token");

        assertEquals(101L, result.userId());
        assertEquals(expectedStep, result.onboardingStep());
        assertEquals(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                userMapper.updatedLastLoginAt
        );
    }

    @Test
    void loginRejectsInvalidPersistedOnboardingStepAsInternalError() {
        userMapper.foundUser = activeUser();
        userMapper.onboardingStep = "UNKNOWN";

        ApiException exception = assertThrows(
                ApiException.class,
                () -> authService.login("Bearer valid-token")
        );

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
    }

    @Test
    void loginRejectsInactiveAccount() {
        User user = activeUser();
        user.setStatus(UserStatus.SUSPENDED);
        userMapper.foundUser = user;

        ApiException exception = assertThrows(
                ApiException.class,
                () -> authService.login("Bearer valid-token")
        );

        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE, exception.getErrorCode());
    }

    @Test
    void loginRequiresSignupWhenFirebaseUserIsNotLinked() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> authService.login("Bearer valid-token")
        );

        assertEquals(ErrorCode.SIGNUP_REQUIRED, exception.getErrorCode());
    }

    @Test
    void logoutMapsInvalidTokenToUnauthorized() {
        authService = createService(token -> {
            throw new InvalidFirebaseTokenException();
        });

        ApiException exception = assertThrows(
                ApiException.class,
                () -> authService.logout("Bearer invalid-token")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    private AuthService createService(FirebaseTokenVerifier verifier) {
        return new AuthService(
                verifier,
                new BearerTokenExtractor(),
                userMapper,
                consentMapper,
                FIXED_CLOCK,
                "terms-v1",
                "privacy-v1"
        );
    }

    private User activeUser() {
        User user = User.signup(
                "firebase-uid-1",
                "student@example.com",
                "새싹투자자",
                LocalDateTime.of(2026, 7, 31, 0, 0)
        );
        user.setUserId(101L);
        return user;
    }

    private static final class FakeUserMapper implements UserMapper {

        private int signupConflictCount;
        private boolean insertCalled;
        private User insertedUser;
        private User foundUser;
        private LocalDateTime updatedLastLoginAt;
        private String onboardingStep = "HOME";

        @Override
        public int countSignupConflicts(
                String firebaseUid,
                String email,
                String nickname
        ) {
            return signupConflictCount;
        }

        @Override
        public int insert(User user) {
            insertCalled = true;
            insertedUser = user;
            user.setUserId(101L);
            return 1;
        }

        @Override
        public User findByFirebaseUid(String firebaseUid) {
            return foundUser;
        }

        @Override public User findById(long userId) { return null; }
        @Override public int countNicknameConflict(long userId, String nickname) { return 0; }
        @Override public int updateProfile(long userId, String nickname, Boolean newsletterOptIn, LocalDateTime updatedAt) { return 0; }

        @Override
        public int updateLastLoginAt(long userId, LocalDateTime lastLoginAt) {
            updatedLastLoginAt = lastLoginAt;
            return 1;
        }

        @Override
        public String findOnboardingStep(long userId) {
            return onboardingStep;
        }
    }

    private static final class FakeUserConsentMapper
            implements UserConsentMapper {

        private final List<UserConsent> consents = new ArrayList<>();

        @Override
        public int insert(UserConsent consent) {
            consents.add(consent);
            return 1;
        }
    }
}
