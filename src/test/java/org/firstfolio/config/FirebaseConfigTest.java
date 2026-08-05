package org.firstfolio.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirebaseConfigTest {

    @Test
    void doesNotLoadCredentialsUntilFirebaseIsUsed() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "firebaseConfigTest",
                            Map.of("firebase.project-id", "firstfolio-test")
                    )
            );
            context.register(FirebaseConfig.class);
            context.refresh();

            assertTrue(context.containsBean("firebaseApp"));
            assertTrue(context.containsBean("firebaseAuth"));
            assertFalse(
                    context.getBeanFactory().containsSingleton("firebaseApp")
            );
            assertFalse(
                    context.getBeanFactory().containsSingleton("firebaseAuth")
            );
        }
    }
}
