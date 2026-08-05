package org.firstfolio.auth.service;

import org.firstfolio.auth.domain.VerifiedFirebaseUser;

public interface FirebaseTokenVerifier {

    VerifiedFirebaseUser verify(String idToken);
}
