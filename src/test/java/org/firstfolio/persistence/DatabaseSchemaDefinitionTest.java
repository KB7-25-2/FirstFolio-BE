package org.firstfolio.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaDefinitionTest {

    @Test
    void levelTestAttemptIsIntegratedAndUniquePerUser() throws IOException {
        String schema = normalize(Files.readString(Path.of("database.sql")));

        assertTrue(schema.contains(
                "CASE WHEN quiz_type = 'LEVEL_TEST' THEN user_id ELSE NULL END"
        ));
        assertTrue(schema.contains(
                "CONSTRAINT uq_quiz_attempts_level_test_user UNIQUE (level_test_user_id)"
        ));
        assertTrue(schema.contains(
                "quiz_type = 'LEVEL_TEST' AND main_chapter_id IS NULL "
                        + "AND sub_chapter_id IS NULL "
                        + "AND content_version_id IS NULL"
        ));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
