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

    @Test
    void dailyQuestSchemaUsesQuestionUsageAndQuestDate() throws IOException {
        String schema = normalize(Files.readString(Path.of("database.sql")));

        assertTrue(schema.contains(
                "quest_date DATE NULL COMMENT "
                        + "'DAILY_NEWS 문항을 제공할 서비스 기준 날짜'"
        ));
        assertTrue(schema.contains(
                "usage_type = 'DAILY_GENERAL' "
                        + "AND main_chapter_id IS NOT NULL "
                        + "AND quest_date IS NULL"
        ));
        assertTrue(schema.contains(
                "usage_type = 'DAILY_NEWS' "
                        + "AND main_chapter_id IS NULL "
                        + "AND sub_chapter_id IS NULL "
                        + "AND quest_date IS NOT NULL "
                        + "AND question_type = 'SCENARIO' "
                        + "AND generation_type = 'AI'"
        ));
        assertTrue(schema.contains(
                "CONSTRAINT uq_daily_quest_items_question "
                        + "UNIQUE (daily_quest_id, question_id)"
        ));
        assertTrue(schema.contains(
                "reward_policy_id BIGINT NULL COMMENT "
                        + "'완료 시 적용한 DAILY_QUEST_REWARD 정책 버전'"
        ));
        assertTrue(!schema.contains(
                "source_type VARCHAR(20) NOT NULL COMMENT "
                        + "'GENERAL, WRONG_RETRY, NEWS'"
        ));
    }

    @Test
    void dailyQuestSchemaChangesAreTrackedByFlywayV4() throws IOException {
        String migration = normalize(Files.readString(Path.of(
                "src/main/resources/db/migration/V4__finalize_daily_quest_schema.sql"
        )));

        assertTrue(migration.contains(
                "ADD COLUMN quest_date DATE NULL COMMENT "
                        + "'DAILY_NEWS 문항을 제공할 서비스 기준 날짜'"
        ));
        assertTrue(migration.contains(
                "ADD INDEX idx_quiz_questions_daily_date "
                        + "( usage_type, quest_date, status )"
        ));
        assertTrue(migration.contains(
                "ADD COLUMN reward_policy_id BIGINT NULL COMMENT "
                        + "'완료 시 적용한 DAILY_QUEST_REWARD 정책 버전'"
        ));
        assertTrue(migration.contains(
                "ADD CONSTRAINT uq_daily_quests_point_transaction "
                        + "UNIQUE (point_transaction_id)"
        ));
        assertTrue(migration.contains(
                "ADD CONSTRAINT fk_daily_quests_reward_policy "
                        + "FOREIGN KEY (reward_policy_id)"
        ));
        assertTrue(migration.contains("DROP COLUMN source_type"));
        assertTrue(migration.contains(
                "ADD CONSTRAINT uq_daily_quest_items_question "
                        + "UNIQUE (daily_quest_id, question_id)"
        ));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
