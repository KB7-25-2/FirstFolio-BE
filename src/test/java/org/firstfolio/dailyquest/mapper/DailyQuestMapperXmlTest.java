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
                "findItemByIdAndUserIdForUpdate",
                "countItemsByDailyQuestId",
                "countAnsweredItemsByDailyQuestId",
                "insertQuest",
                "insertItem"
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
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
