package org.firstfolio.dailyquest.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyQuestMapperXmlTest {

    private static final String RESOURCE =
            "mappers/dailyquest/DailyQuestMapper.xml";

    @Test
    void parsesMapperAndRegistersFoundationStatements() throws IOException {
        Configuration configuration = new Configuration();

        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            XMLMapperBuilder builder = new XMLMapperBuilder(
                    input,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            );
            builder.parse();
        }

        assertTrue(configuration.hasMapper(DailyQuestMapper.class));
        for (String statement : new String[]{
                "findUserIdForUpdate",
                "findByUserIdAndQuestDate",
                "findByUserIdAndQuestDateForUpdate",
                "findByIdForUpdate",
                "findItemsByDailyQuestId",
                "findUnresolvedWrongAnswers",
                "findRecentlyAssignedGeneralQuestionKeys",
                "findQuestIdByItemIdAndUserId",
                "findItemByIdAndUserIdForUpdate",
                "countItemsByDailyQuestId",
                "countAnsweredItemsByDailyQuestId",
                "insertQuest",
                "insertItem",
                "saveAnswer",
                "markInProgressIfAssigned"
        }) {
            assertTrue(configuration.hasStatement(
                    DailyQuestMapper.class.getName() + "." + statement
            ));
        }

        BoundSql todaySql = configuration.getMappedStatement(
                        DailyQuestMapper.class.getName()
                                + ".findByUserIdAndQuestDateForUpdate"
                )
                .getBoundSql(Map.of(
                        "userId",
                        10L,
                        "questDate",
                        LocalDate.of(2026, 8, 13)
                ));
        assertTrue(normalize(todaySql.getSql()).endsWith("FOR UPDATE"));

        BoundSql itemInsertSql = configuration.getMappedStatement(
                        DailyQuestMapper.class.getName() + ".insertItem"
                )
                .getBoundSql(new org.firstfolio.dailyquest.domain.DailyQuestItem());
        String normalizedInsertSql = normalize(itemInsertSql.getSql());
        assertFalse(normalizedInsertSql.contains("source_type"));
        assertTrue(normalizedInsertSql.contains(
                "INSERT INTO daily_quest_items "
                        + "( daily_quest_id, question_id, display_order"
        ));

        BoundSql wrongAnswersSql = configuration.getMappedStatement(
                        DailyQuestMapper.class.getName()
                                + ".findUnresolvedWrongAnswers"
                )
                .getBoundSql(Map.of("userId", 10L));
        String normalizedWrongAnswersSql = normalize(
                wrongAnswersSql.getSql()
        );
        assertTrue(normalizedWrongAnswersSql.contains(
                "FIRST_VALUE(question.main_chapter_id) OVER "
                        + "( PARTITION BY question.question_key"
        ));
        assertTrue(normalizedWrongAnswersSql.contains(
                "latest.is_correct = FALSE "
                        + "AND latest.later_correct_count = 0"
        ));
        assertTrue(normalizedWrongAnswersSql.contains(
                "COUNT(*) AS wrong_count"
        ));
        assertTrue(normalizedWrongAnswersSql.contains(
                "latest.usage_type = 'LEVEL_TEST' AND EXISTS"
        ));

        BoundSql recentKeysSql = configuration.getMappedStatement(
                        DailyQuestMapper.class.getName()
                                + ".findRecentlyAssignedGeneralQuestionKeys"
                )
                .getBoundSql(Map.of(
                        "userId",
                        10L,
                        "fromDate",
                        LocalDate.of(2026, 8, 6),
                        "questDate",
                        LocalDate.of(2026, 8, 13)
                ));
        String normalizedRecentKeysSql = normalize(recentKeysSql.getSql());
        assertTrue(normalizedRecentKeysSql.contains(
                "quest.quest_date >= ? AND quest.quest_date < ?"
        ));
        assertTrue(normalizedRecentKeysSql.contains(
                "question.usage_type = 'DAILY_GENERAL'"
        ));

        BoundSql itemQuestSql = configuration.getMappedStatement(
                        DailyQuestMapper.class.getName()
                                + ".findQuestIdByItemIdAndUserId"
                )
                .getBoundSql(Map.of(
                        "dailyQuestItemId",
                        5001L,
                        "userId",
                        10L
                ));
        String normalizedItemQuestSql = normalize(itemQuestSql.getSql());
        assertTrue(normalizedItemQuestSql.contains(
                "item.daily_quest_item_id = ? AND quest.user_id = ?"
        ));

        BoundSql answerSaveSql = configuration.getMappedStatement(
                        DailyQuestMapper.class.getName() + ".saveAnswer"
                )
                .getBoundSql(new org.firstfolio.dailyquest.domain.DailyQuestItem());
        String normalizedAnswerSaveSql = normalize(answerSaveSql.getSql());
        assertTrue(normalizedAnswerSaveSql.contains(
                "SET user_answer_json = ?, is_correct = NULL, answered_at = ?"
        ));
        assertTrue(normalizedAnswerSaveSql.endsWith(
                "AND daily_quest_id = ? AND is_correct IS NULL"
        ));

        BoundSql progressSql = configuration.getMappedStatement(
                        DailyQuestMapper.class.getName()
                                + ".markInProgressIfAssigned"
                )
                .getBoundSql(Map.of("dailyQuestId", 4001L));
        assertTrue(normalize(progressSql.getSql()).endsWith(
                "WHERE daily_quest_id = ? AND status = 'ASSIGNED'"
        ));
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
