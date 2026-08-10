package org.firstfolio.auth.service;

import org.firstfolio.auth.dto.request.SignupRequest;

public interface AuthUseCase {

    SignupResult signup(String authorizationHeader, SignupRequest request);

    LoginResult login(String authorizationHeader);

    void logout(String authorizationHeader);
}
