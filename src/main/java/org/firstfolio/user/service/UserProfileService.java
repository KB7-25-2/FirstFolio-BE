package org.firstfolio.user.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.ConsentType;
import org.firstfolio.user.domain.User;
import org.firstfolio.user.domain.UserConsent;
import org.firstfolio.user.dto.request.UserProfilePatchRequest;
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
public class UserProfileService {

    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 10;

    private final UserMapper userMapper;
    private final UserConsentMapper userConsentMapper;
    private final Clock clock;
    private final String newsletterPolicyVersion;

    public UserProfileService(
            UserMapper userMapper,
            UserConsentMapper userConsentMapper,
            Clock clock,
            @Value("${user.newsletter-policy-version:}") String newsletterPolicyVersion
    ) {
        this.userMapper = userMapper;
        this.userConsentMapper = userConsentMapper;
        this.clock = clock;
        this.newsletterPolicyVersion = newsletterPolicyVersion;
    }

    @Transactional(readOnly = true)
    public User get(long userId) {
        return requireUser(userId);
    }

    @Transactional
    public User patch(long userId, UserProfilePatchRequest request) {
        if (request == null
                || (request.nickname() == null && request.newsletterOptIn() == null)) {
            throw new ApiException(ErrorCode.NO_PATCH_FIELDS);
        }

        User current = requireUser(userId);
        String nickname = normalizeNickname(request.nickname());

        if (nickname != null && userMapper.countNicknameConflict(userId, nickname) > 0) {
            throw new ApiException(ErrorCode.NICKNAME_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Boolean newsletterOptIn = request.newsletterOptIn();

        if (newsletterOptIn != null && newsletterOptIn != current.isNewsletterOptIn()) {
            if (!StringUtils.hasText(newsletterPolicyVersion)) {
                throw new IllegalStateException("NEWSLETTER_POLICY_VERSION is required.");
            }
            userConsentMapper.insert(new UserConsent(
                    userId, ConsentType.NEWSLETTER,
                    newsletterPolicyVersion, newsletterOptIn, now
            ));
        }

        try {
            userMapper.updateProfile(userId, nickname, newsletterOptIn, now);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(ErrorCode.NICKNAME_CONFLICT);
        }

        return requireUser(userId);
    }

    private User requireUser(long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private String normalizeNickname(String raw) {
        if (raw == null) {
            return null;
        }
        if (!StringUtils.hasText(raw)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "닉네임 형식이 올바르지 않습니다.");
        }
        String nickname = raw.trim();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < NICKNAME_MIN_LENGTH || length > NICKNAME_MAX_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "닉네임은 2자 이상 10자 이하여야 합니다.");
        }
        return nickname;
    }
}
