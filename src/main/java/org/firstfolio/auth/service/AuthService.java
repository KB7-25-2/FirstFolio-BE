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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AuthService implements AuthUseCase {

    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 10;

    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final BearerTokenExtractor bearerTokenExtractor;
    private final UserMapper userMapper;
    private final UserConsentMapper userConsentMapper;
    private final Clock clock;
    private final String termsOfServiceVersion;
    private final String privacyPolicyVersion;

    public AuthService(
            FirebaseTokenVerifier firebaseTokenVerifier,
            BearerTokenExtractor bearerTokenExtractor,
            UserMapper userMapper,
            UserConsentMapper userConsentMapper,
            Clock clock,
            @Value("${auth.terms-of-service-version:}") String termsOfServiceVersion,
            @Value("${auth.privacy-policy-version:}") String privacyPolicyVersion
    ) {
        this.firebaseTokenVerifier = firebaseTokenVerifier;
        this.bearerTokenExtractor = bearerTokenExtractor;
        this.userMapper = userMapper;
        this.userConsentMapper = userConsentMapper;
        this.clock = clock;
        this.termsOfServiceVersion = termsOfServiceVersion;
        this.privacyPolicyVersion = privacyPolicyVersion;
    }

    @Override
    @Transactional
    public SignupResult signup(
            String authorizationHeader,
            SignupRequest request
    ) {
        String nickname = validateAndNormalizeSignupRequest(request);
        VerifiedFirebaseUser firebaseUser = verifyToken(
                authorizationHeader,
                ErrorCode.INVALID_ID_TOKEN
        );
        validateConsentPolicyVersions();

        if (userMapper.countSignupConflicts(
                firebaseUser.uid(),
                firebaseUser.email(),
                nickname
        ) > 0) {
            throw accountConflict();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        User user = User.signup(
                firebaseUser.uid(),
                firebaseUser.email(),
                nickname,
                now
        );

        try {
            userMapper.insert(user);
            saveRequiredConsent(
                    user.getUserId(),
                    ConsentType.TERMS_OF_SERVICE,
                    termsOfServiceVersion,
                    now
            );
            saveRequiredConsent(
                    user.getUserId(),
                    ConsentType.PRIVACY,
                    privacyPolicyVersion,
                    now
            );
        } catch (DuplicateKeyException exception) {
            throw accountConflict();
        }

        return new SignupResult(
                user.getUserId(),
                user.getNickname(),
                user.getRoleCode(),
                OnboardingStep.LEVEL_TEST
        );
    }

    @Override
    @Transactional
    public LoginResult login(String authorizationHeader) {
        VerifiedFirebaseUser firebaseUser = verifyToken(
                authorizationHeader,
                ErrorCode.INVALID_ID_TOKEN
        );
        User user = userMapper.findByFirebaseUid(firebaseUser.uid());

        if (user == null) {
            throw new ApiException(ErrorCode.SIGNUP_REQUIRED);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        LocalDateTime loginAt = LocalDateTime.now(clock);
        userMapper.updateLastLoginAt(user.getUserId(), loginAt);
        OnboardingStep onboardingStep = OnboardingStep.valueOf(
                userMapper.findOnboardingStep(user.getUserId())
        );

        return new LoginResult(
                user.getUserId(),
                user.getNickname(),
                user.getRoleCode(),
                onboardingStep
        );
    }

    @Override
    public void logout(String authorizationHeader) {
        verifyToken(
                authorizationHeader,
                ErrorCode.UNAUTHORIZED
        );
    }

    private String validateAndNormalizeSignupRequest(SignupRequest request) {
        if (request == null
                || !Boolean.TRUE.equals(request.requiredTermsAgreed())
                || !StringUtils.hasText(request.nickname())) {
            throw invalidSignupInput();
        }

        String nickname = request.nickname().trim();
        int nicknameLength = nickname.codePointCount(0, nickname.length());

        if (nicknameLength < NICKNAME_MIN_LENGTH
                || nicknameLength > NICKNAME_MAX_LENGTH) {
            throw invalidSignupInput();
        }

        return nickname;
    }

    private void validateConsentPolicyVersions() {
        if (!StringUtils.hasText(termsOfServiceVersion)
                || !StringUtils.hasText(privacyPolicyVersion)) {
            throw new IllegalStateException(
                    "Required consent policy versions are not configured."
            );
        }
    }

    private VerifiedFirebaseUser verifyToken(
            String authorizationHeader,
            ErrorCode errorCode
    ) {
        try {
            String idToken = bearerTokenExtractor.extract(authorizationHeader);
            return firebaseTokenVerifier.verify(idToken);
        } catch (InvalidFirebaseTokenException exception) {
            throw new ApiException(errorCode);
        }
    }

    private void saveRequiredConsent(
            long userId,
            ConsentType type,
            String policyVersion,
            LocalDateTime now
    ) {
        userConsentMapper.insert(
                new UserConsent(userId, type, policyVersion, true, now)
        );
    }

    private ApiException invalidSignupInput() {
        return new ApiException(ErrorCode.INVALID_SIGNUP_INPUT);
    }

    private ApiException accountConflict() {
        return new ApiException(ErrorCode.ACCOUNT_CONFLICT);
    }
}
