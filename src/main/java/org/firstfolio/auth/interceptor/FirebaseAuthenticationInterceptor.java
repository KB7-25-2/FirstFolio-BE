package org.firstfolio.auth.interceptor;

import org.firstfolio.auth.domain.AuthenticatedUser;
import org.firstfolio.auth.domain.VerifiedFirebaseUser;
import org.firstfolio.auth.exception.InvalidFirebaseTokenException;
import org.firstfolio.auth.service.BearerTokenExtractor;
import org.firstfolio.auth.service.FirebaseTokenVerifier;
import org.firstfolio.auth.web.AuthenticationRequestAttributes;
import org.firstfolio.exception.ApiException;
import org.firstfolio.user.domain.User;
import org.firstfolio.user.domain.UserStatus;
import org.firstfolio.user.mapper.UserMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class FirebaseAuthenticationInterceptor implements HandlerInterceptor {

    private final BearerTokenExtractor bearerTokenExtractor;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final UserMapper userMapper;

    public FirebaseAuthenticationInterceptor(
            BearerTokenExtractor bearerTokenExtractor,
            FirebaseTokenVerifier firebaseTokenVerifier,
            UserMapper userMapper
    ) {
        this.bearerTokenExtractor = bearerTokenExtractor;
        this.firebaseTokenVerifier = firebaseTokenVerifier;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        VerifiedFirebaseUser firebaseUser = verifyToken(
                request.getHeader("Authorization")
        );
        User user = userMapper.findByFirebaseUid(firebaseUser.uid());

        if (user == null) {
            throw unauthorized();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE",
                    "이용할 수 없는 계정 상태입니다."
            );
        }

        request.setAttribute(
                AuthenticationRequestAttributes.CURRENT_USER,
                new AuthenticatedUser(
                        user.getUserId(),
                        user.getFirebaseUid(),
                        user.getNickname(),
                        user.getRoleCode()
                )
        );

        return true;
    }

    private VerifiedFirebaseUser verifyToken(String authorizationHeader) {
        try {
            String idToken = bearerTokenExtractor.extract(authorizationHeader);
            return firebaseTokenVerifier.verify(idToken);
        } catch (InvalidFirebaseTokenException exception) {
            throw unauthorized();
        }
    }

    private ApiException unauthorized() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "인증 토큰이 없거나 유효하지 않습니다."
        );
    }
}
