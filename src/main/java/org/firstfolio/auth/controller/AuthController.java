package org.firstfolio.auth.controller;

import org.firstfolio.api.ApiResponse;
import org.firstfolio.auth.dto.request.SignupRequest;
import org.firstfolio.auth.dto.response.LoginResponse;
import org.firstfolio.auth.dto.response.SignupResponse;
import org.firstfolio.auth.service.AuthUseCase;
import org.firstfolio.auth.service.LoginResult;
import org.firstfolio.auth.service.SignupResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorizationHeader,
            @RequestBody(required = false) SignupRequest request
    ) {
        SignupResult result = authUseCase.signup(authorizationHeader, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(SignupResponse.from(result)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorizationHeader
    ) {
        LoginResult result = authUseCase.login(authorizationHeader);
        return ApiResponse.of(LoginResponse.from(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(
                    value = HttpHeaders.AUTHORIZATION,
                    required = false
            ) String authorizationHeader
    ) {
        authUseCase.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }
}
