package org.firstfolio.user.service;

import org.firstfolio.exception.ApiException;
import org.firstfolio.exception.ErrorCode;
import org.firstfolio.user.domain.User;
import org.firstfolio.user.domain.UserConsent;
import org.firstfolio.user.dto.request.UserProfilePatchRequest;
import org.firstfolio.user.mapper.UserConsentMapper;
import org.firstfolio.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {

    private UserMapper userMapper;
    private UserConsentMapper consentMapper;
    private UserProfileService service;
    private User user;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        consentMapper = mock(UserConsentMapper.class);
        service = new UserProfileService(
                userMapper, consentMapper,
                Clock.fixed(Instant.parse("2026-08-06T03:00:00Z"), ZoneOffset.UTC),
                "newsletter-v1"
        );
        user = User.signup("firebase-1", "user@example.com", "새싹투자자",
                LocalDateTime.of(2026, 8, 1, 0, 0));
        user.setUserId(101L);
        when(userMapper.findById(101L)).thenReturn(user);
    }

    @Test
    void getsCurrentUserProfile() {
        assertSame(user, service.get(101L));
    }

    @Test
    void patchesNicknameAndNewsletterWithConsentHistory() {
        when(userMapper.findById(101L)).thenAnswer(invocation -> user);
        doAnswer(invocation -> {
            user.setNickname(invocation.getArgument(1));
            user.setNewsletterOptIn(invocation.getArgument(2));
            user.setUpdatedAt(invocation.getArgument(3));
            return 1;
        }).when(userMapper).updateProfile(eq(101L), anyString(), eq(true), any());

        User updated = service.patch(101L, new UserProfilePatchRequest(" 채권꿈나무 ", true));

        assertEquals("채권꿈나무", updated.getNickname());
        assertTrue(updated.isNewsletterOptIn());
        verify(consentMapper).insert(any(UserConsent.class));
    }

    @Test
    void rejectsEmptyPatch() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.patch(101L, new UserProfilePatchRequest(null, null)));
        assertEquals(ErrorCode.NO_PATCH_FIELDS, exception.getErrorCode());
    }

    @Test
    void rejectsDuplicatedNickname() {
        when(userMapper.countNicknameConflict(101L, "중복닉네임")).thenReturn(1);
        ApiException exception = assertThrows(ApiException.class,
                () -> service.patch(101L, new UserProfilePatchRequest("중복닉네임", null)));
        assertEquals(ErrorCode.NICKNAME_CONFLICT, exception.getErrorCode());
    }
}
