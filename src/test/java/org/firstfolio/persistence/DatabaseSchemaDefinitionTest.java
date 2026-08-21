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

    @Test
    void gifticonSchemaUsesEncryptedIndividualCodeInventory() throws IOException {
        String schema = normalize(Files.readString(Path.of("database.sql")));

        assertTrue(schema.contains("CREATE TABLE gifticon_codes"));
        assertTrue(schema.contains("code_ciphertext VARBINARY(1024) NOT NULL"));
        assertTrue(!schema.contains("barcode_ciphertext"));
        assertTrue(schema.contains(
                "CONSTRAINT uq_gifticon_codes_product_fingerprint "
                        + "UNIQUE (gifticon_product_id, code_fingerprint)"
        ));
        assertTrue(schema.contains(
                "status IN ('AVAILABLE', 'ASSIGNED', 'VOID')"
        ));
        assertTrue(schema.contains(
                "CONSTRAINT uq_gifticon_orders_code UNIQUE (gifticon_code_id)"
        ));
        assertTrue(schema.contains(
                "CONSTRAINT uq_gifticon_orders_user_idempotency "
                        + "UNIQUE (user_id, idempotency_key)"
        ));
        assertTrue(schema.contains("CREATE TABLE gifticon_code_access_logs"));
        assertTrue(!schema.contains("stock_quantity INT NOT NULL DEFAULT 0"));
        assertTrue(!schema.contains("delivery_info VARCHAR(255)"));
        assertTrue(!schema.contains("provider_reference VARCHAR(255)"));
    }

    @Test
    void gifticonSchemaChangesAreTrackedByFlywayV5() throws IOException {
        String migration = normalize(Files.readString(Path.of(
                "src/main/resources/db/migration/V5__redesign_gifticon_market.sql"
        )));

        assertTrue(migration.contains("DROP TABLE gifticon_orders"));
        assertTrue(migration.contains("DROP TABLE gifticon_products"));
        assertTrue(migration.contains("CREATE TABLE gifticon_products"));
        assertTrue(migration.contains("CREATE TABLE gifticon_codes"));
        assertTrue(migration.contains("CREATE TABLE gifticon_orders"));
        assertTrue(migration.contains("CREATE TABLE gifticon_code_access_logs"));
        assertTrue(migration.contains(
                "required_points > 0 AND required_points = face_value_krw"
        ));
        assertTrue(migration.contains(
                "CONSTRAINT uq_gifticon_orders_point_transaction "
                        + "UNIQUE (point_transaction_id)"
        ));
    }

    @Test
    void dailyCandleSchemaIsTrackedByFlywayV8() throws IOException {
        String schema = normalize(Files.readString(Path.of("database.sql")));
        String migration = normalize(Files.readString(Path.of(
                "src/main/resources/db/migration/V8__create_product_daily_candles.sql"
        )));

        for (String definition : new String[]{
                "CREATE TABLE product_daily_candles",
                "CONSTRAINT uq_product_daily_candles_product_date UNIQUE (product_id, trade_date)",
                "CONSTRAINT chk_product_daily_candles_source CHECK (source_type = 'TOSS_INVEST')",
                "INDEX idx_product_daily_candles_latest (product_id, trade_date DESC)"
        }) {
            assertTrue(schema.contains(definition));
            assertTrue(migration.contains(definition));
        }
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
