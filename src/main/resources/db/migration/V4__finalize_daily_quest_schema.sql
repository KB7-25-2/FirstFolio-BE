-- Finalize the daily quest schema defined in PROJECT_SPEC.md.
--
-- This migration intentionally rejects legacy DAILY_NEWS rows because their
-- service date cannot be inferred safely. Export and reinsert them with a
-- quest_date after V4, or remove them before applying V4. The validations run
-- before any ALTER TABLE statement so a failed migration does not leave
-- MySQL's non-transactional DDL half-applied.

DROP PROCEDURE IF EXISTS validate_v4_daily_quest_schema;

DELIMITER //

CREATE PROCEDURE validate_v4_daily_quest_schema()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM quiz_questions
        WHERE usage_type = 'DAILY_NEWS'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4 requires legacy DAILY_NEWS rows to be removed or migrated separately';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM quiz_questions
        WHERE (usage_type = 'SUB_CHAPTER'
                AND (main_chapter_id IS NULL OR sub_chapter_id IS NULL))
           OR (usage_type IN ('LEVEL_TEST', 'MAIN_CHAPTER')
                AND (main_chapter_id IS NULL OR sub_chapter_id IS NOT NULL))
           OR (usage_type = 'DAILY_GENERAL'
                AND main_chapter_id IS NULL)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4 found quiz_questions rows that violate the finalized question scope';
    END IF;

    IF EXISTS (
        SELECT point_transaction_id
        FROM daily_quests
        WHERE point_transaction_id IS NOT NULL
        GROUP BY point_transaction_id
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4 found duplicate daily_quests.point_transaction_id values';
    END IF;

    IF EXISTS (
        SELECT daily_quest_id, question_id
        FROM daily_quest_items
        GROUP BY daily_quest_id, question_id
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4 found duplicate question assignments in daily_quest_items';
    END IF;
END//

DELIMITER ;

CALL validate_v4_daily_quest_schema();
DROP PROCEDURE validate_v4_daily_quest_schema;

ALTER TABLE quiz_questions
    ADD COLUMN quest_date DATE NULL
        COMMENT 'DAILY_NEWS 문항을 제공할 서비스 기준 날짜'
        AFTER generation_type,
    DROP CHECK chk_quiz_questions_scope,
    ADD CONSTRAINT chk_quiz_questions_scope CHECK (
        (
            usage_type = 'SUB_CHAPTER'
            AND main_chapter_id IS NOT NULL
            AND sub_chapter_id IS NOT NULL
            AND quest_date IS NULL
        )
        OR
        (
            usage_type IN ('LEVEL_TEST', 'MAIN_CHAPTER')
            AND main_chapter_id IS NOT NULL
            AND sub_chapter_id IS NULL
            AND quest_date IS NULL
        )
        OR
        (
            usage_type = 'DAILY_GENERAL'
            AND main_chapter_id IS NOT NULL
            AND quest_date IS NULL
        )
        OR
        (
            usage_type = 'DAILY_NEWS'
            AND main_chapter_id IS NULL
            AND sub_chapter_id IS NULL
            AND quest_date IS NOT NULL
            AND question_type = 'SCENARIO'
            AND generation_type = 'AI'
        )
    ),
    ADD INDEX idx_quiz_questions_daily_date (
        usage_type,
        quest_date,
        status
    );

ALTER TABLE daily_quests
    ADD COLUMN reward_policy_id BIGINT NULL
        COMMENT '완료 시 적용한 DAILY_QUEST_REWARD 정책 버전'
        AFTER score,
    ADD CONSTRAINT uq_daily_quests_point_transaction
        UNIQUE (point_transaction_id),
    ADD CONSTRAINT fk_daily_quests_reward_policy
        FOREIGN KEY (reward_policy_id) REFERENCES system_policies (policy_id)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE daily_quest_items
    DROP CHECK chk_daily_quest_items_source,
    DROP COLUMN source_type,
    ADD CONSTRAINT uq_daily_quest_items_question
        UNIQUE (daily_quest_id, question_id);
