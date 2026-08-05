package org.firstfolio.auth.service;

import org.firstfolio.auth.exception.InvalidFirebaseTokenException;
import org.springframework.stereotype.Component;

@Component
public class BearerTokenExtractor {

    private static final String BEARER_PREFIX = "Bearer ";

    public String extract(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidFirebaseTokenException();
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();

        if (token.isEmpty() || token.contains(" ")) {
            throw new InvalidFirebaseTokenException();
        }

        return token;
    }
}
