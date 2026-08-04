package org.firstfolio.common.security;

/**
 * 요청을 보낸 사용자. 인증 방식이 무엇으로 확정되든 이 형태는 그대로 쓸 수 있게 둔다.
 */
public final class CurrentUser {

    private final Long userId;
    private final UserRole role;

    public CurrentUser(Long userId, UserRole role) {
        this.userId = userId;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
