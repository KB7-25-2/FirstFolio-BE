package org.firstfolio.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.firstfolio.auth.domain.VerifiedFirebaseUser;
import org.firstfolio.auth.exception.InvalidFirebaseTokenException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    private final ObjectProvider<FirebaseAuth> firebaseAuthProvider;

    public FirebaseAdminTokenVerifier(
            ObjectProvider<FirebaseAuth> firebaseAuthProvider
    ) {
        this.firebaseAuthProvider = firebaseAuthProvider;
    }

    @Override
    public VerifiedFirebaseUser verify(String idToken) {
        try {
            FirebaseToken token = firebaseAuthProvider.getObject()
                    .verifyIdToken(idToken, true);

            return new VerifiedFirebaseUser(token.getUid(), token.getEmail());
        } catch (FirebaseAuthException | IllegalArgumentException exception) {
            throw new InvalidFirebaseTokenException(exception);
        }
    }
}
