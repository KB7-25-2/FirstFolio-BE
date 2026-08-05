package org.firstfolio.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Lazy
@Configuration
public class FirebaseConfig {

    private static final String APP_NAME = "firstfolio";

    @Bean(destroyMethod = "delete")
    public FirebaseApp firebaseApp(
            @Value("${firebase.project-id:}") String projectId
    ) throws IOException {
        if (!StringUtils.hasText(projectId)) {
            throw new IllegalStateException(
                    "FIREBASE_PROJECT_ID environment variable is required."
            );
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projectId)
                .build();

        return FirebaseApp.initializeApp(options, APP_NAME);
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
