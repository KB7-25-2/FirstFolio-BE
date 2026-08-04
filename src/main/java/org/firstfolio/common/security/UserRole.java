package org.firstfolio.common.security;

/**
 * {@code users.role_code}와 값이 같아야 한다.
 */
public enum UserRole {

    USER,
    ADMIN;

    public static UserRole from(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }

        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return USER;
        }
    }
}
